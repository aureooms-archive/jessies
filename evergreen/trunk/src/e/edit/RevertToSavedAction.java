package e.edit;

import java.awt.event.*;

/**
The ETextArea revert-to-saved action.
*/
public class RevertToSavedAction extends ETextAction {
    public static final String ACTION_NAME = "Revert to Saved";
    
    public RevertToSavedAction() {
        super(ACTION_NAME);
    }
    
    public void actionPerformed(ActionEvent e) {
        ETextWindow window = getFocusedTextWindow();
        if (window == null) {
            return;
        }
        if (window.isDirty() || window.isOutOfDateWithRespectToDisk()) {
            window.revertToSaved();
        } else {
            Edit.showAlert(ACTION_NAME, "'" + window.getFilename() + "' has not been modified in the editor or on disk.");
        }
    }
}
