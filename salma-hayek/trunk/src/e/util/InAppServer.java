package e.util;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/**
 * If your application wants to provide a simple server that users (or scripts)
 * can connect to and make requests, you might find this handy. Simply supply
 * an interface listing available commands, and provide an object that
 * implements that interface to do the work, and you're off!
 * 
 * Edit uses this so you can open files from the shell, and Terminator uses it
 * so that successive invocations don't need to start a new VM.
 */
public final class InAppServer {
    private String fullName;
    
    // I think that having a generic constructor provides all the safety we
    // can get from generics, and that keeping the type information here would
    // only add inconvenience because we'd need to make this a generic class.
    private Class exportedInterface;
    private Object handler;
    
    /**
     * 'handler' can be of any type that implements 'exportedInterface', but
     * only methods declared by the interface (and its superinterfaces) will be
     * invocable.
     */
    public <T> InAppServer(String name, String portFilename, Class<T> exportedInterface, T handler) {
        this.fullName = name + "Server";
        this.exportedInterface = exportedInterface;
        this.handler = handler;
        
        // In the absence of authentication, we shouldn't risk starting a server as root.
        if (System.getProperty("user.name").equals("root")) {
            Log.warn("Refusing to start unauthenticated server '" + fullName + "' as root!");
            return;
        }
        
        try {
            File portFile = FileUtilities.fileFromString(portFilename);
            File serverFifoFile = new File(portFile.getParentFile(), "server-fifo");
            Thread serverThread = new Thread(new ConnectionAccepter(serverFifoFile, portFile), fullName);
            // If there are no other threads left, the InApp server shouldn't keep us alive.
            serverThread.setDaemon(true);
            serverThread.start();
        } catch (Throwable th) {
            Log.warn("Couldn't start " + fullName, th);
        }
    }
    
    public boolean handleCommand(String line, PrintWriter out) {
        String[] split = line.split("[\t ]");
        String commandName = split[0];
        
        try {
            Method[] methods = exportedInterface.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(commandName) && method.getReturnType() == void.class) {
                    return invokeMethod(line, out, method, split);
                }
            }
            throw new NoSuchMethodException();
        } catch (NoSuchMethodException nsmex) {
            out.println(fullName + ": didn't understand request \"" + line + "\".");
        } catch (Exception ex) {
            ex.printStackTrace();
            String s = fullName + ": request denied \"" + line + "\" (" + ex.toString() + ").";
            out.println(s);
        } finally {
            out.flush();
            out.close();
        }
        return false;
    }
    
    private boolean invokeMethod(String line, PrintWriter out, Method method, String[] fields) throws IllegalAccessException, InvocationTargetException {
        ArrayList<Object> methodArguments = new ArrayList<Object>();
        
        Class[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 2 && parameterTypes[0] == PrintWriter.class && parameterTypes[1] == String.class) {
            // FIXME: there must be a better way to say "I want to parse the line myself."
            methodArguments.add(out);
            methodArguments.add(line);
        } else {
            int nextField = 1;
            for (Class parameterType : parameterTypes) {
                if (parameterType == PrintWriter.class) {
                    methodArguments.add(out);
                } else if (parameterType == String.class) {
                    methodArguments.add(fields[nextField++]);
                }
                // FIXME: support other common types. "int" seems a likely first candidate.
            }
        }
        
        method.invoke(handler, methodArguments.toArray());
        return true;
    }
    
    private class ConnectionAccepter implements Runnable {
        private IPC.ConnectionListener listener;
        
        private ConnectionAccepter(File serverFifo, File portFile) throws IOException {
            try {
                if (serverFifo != null) {
                    listener = new IPC.FifoConnectionListener(serverFifo);
                    StringUtilities.writeFile(portFile, "fifo:" + serverFifo.getPath() + "\n");
                }
            } catch (IOException ex) {
                Log.warn("Failed to use FIFO for InAppServer: falling back to server socket.", ex);
                ServerSocket socket = new ServerSocket();
                socket.bind(null);
                this.listener = new IPC.SocketConnectionListener(socket);
                writeHostAndPortToFile(socket, portFile);
            }
        }
        
        private void writeHostAndPortToFile(ServerSocket socket, File portFile) {
            String host = socket.getInetAddress().getHostName();
            int port = socket.getLocalPort();
            // The motivation for the Log.warn would be better satisfied by Bug 38.
            Log.warn("echo " + host + ":" + port + " > " + portFile);
            StringUtilities.writeFile(portFile, "socket:" + host + ":" + port + "\n");
        }
        
        public void run() {
            try {
                while (true) {
                    IPC.Connection connection = listener.accept();
                    String handlerName = Thread.currentThread().getName() + "-Handler-" + Thread.activeCount();
                    new Thread(new ClientHandler(connection), handlerName).start();
                }
            } catch (IOException ex) {
                Log.warn("Connection listener has had to shut down.", ex);
            }
        }
    }
    
    private final class ClientHandler implements Runnable {
        private IPC.Connection connection;
        
        private BufferedReader in;
        private PrintWriter out;
        
        private ClientHandler(IPC.Connection connection) {
            this.connection = connection;
        }
        
        public void run() {
            handleClient();
        }
        
        private void handleClient() {
            try {
                this.in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                this.out = new PrintWriter(new OutputStreamWriter(connection.getOutputStream()));
                handleRequest();
                out.flush();
                out.close();
                in.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                closeClientConnection();
            }
        }
        
        private void closeClientConnection() {
            connection.close();
        }
        
        private void handleRequest() throws IOException {
            String line = in.readLine();
            if (line == null || line.length() == 0) {
                Log.warn(Thread.currentThread().getName() + ": ignoring empty request");
                return;
            }
            if (handleCommand(line, out) == false) {
                out.println(Thread.currentThread().getName() + ": didn't understand request \"" + line + "\"");
            }
        }
    }
}
