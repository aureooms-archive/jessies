package e.ptextarea;

public class PCppIndenter extends PCFamilyIndenter {
    public PCppIndenter(PTextArea textArea) {
        super(textArea);
    }
    
    @Override
    public boolean isInNeedOfClosingSemicolon(String line) {
        return line.matches(".*\\b(class|enum|struct|union)\\b.*");
    }
    
    @Override
    protected boolean isAccessSpecifier(String activePartOfLine) {
        return activePartOfLine.matches("(private|public|protected)\\s*:");
    }
    
    @Override
    protected boolean isNamespace(String activePartOfLine) {
        return activePartOfLine.matches("namespace\\s*\\S*\\s*\\{");
    }
    
    private static boolean isCppAccessSpecifier(String activePartOfLine) {
        return activePartOfLine.matches("(private|public|protected)\\s*:");
    }
    
    @Override
    protected boolean isLabel(String activePartOfLine) {
        return isCppAccessSpecifier(activePartOfLine) || isSwitchLabel(activePartOfLine);
    }
}
