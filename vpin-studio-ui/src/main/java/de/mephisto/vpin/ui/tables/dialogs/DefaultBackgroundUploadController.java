package de.mephisto.vpin.ui.tables.dialogs;

import de.mephisto.vpin.commons.fx.DialogController;
import de.mephisto.vpin.commons.utils.WidgetFactory;
import de.mephisto.vpin.restclient.representations.GameRepresentation;
import de.mephisto.vpin.ui.Studio;
import de.mephisto.vpin.ui.util.Dialogs;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static de.mephisto.vpin.ui.Studio.stage;

public class DefaultBackgroundUploadController implements Initializable, DialogController {
  private final static Logger LOG = LoggerFactory.getLogger(DefaultBackgroundUploadController.class);

  private static File lastFolderSelection;
  private static boolean uploadTypeGeneratorSelectedLast;

  @FXML
  private TextField fileNameField;

  @FXML
  private Button uploadBtn;

  @FXML
  private Label titleLabel;

  private File selection;

  private boolean result = false;
  private GameRepresentation game;

  @FXML
  private void onCancelClick(ActionEvent e) {
    Stage stage = (Stage) ((Button) e.getSource()).getScene().getWindow();
    stage.close();
  }

  @FXML
  private void onUploadClick(ActionEvent event) {
    if (selection != null && selection.exists()) {
      Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
      result = true;
      try {
        uploadTypeGeneratorSelectedLast = false;
        DefaultBackgroundUploadProgressModel model = new DefaultBackgroundUploadProgressModel(this.game.getId(), "Default Background Upload", selection);
        Dialogs.createProgressDialog(model);
      } catch (Exception e) {
        LOG.error("Upload failed: " + e.getMessage(), e);
        WidgetFactory.showAlert(Studio.stage, "Uploading default background failed.", "Please check the log file for details.", "Error: " + e.getMessage());
      } finally {
        stage.close();
      }
    }
  }

  @FXML
  private void onFileSelect(ActionEvent event) {
    Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Picture File");
    fileChooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Pictures", "*.png", "*.jpg", "*.jpeg"));

    if (DefaultBackgroundUploadController.lastFolderSelection != null) {
      fileChooser.setInitialDirectory(DefaultBackgroundUploadController.lastFolderSelection);
    }

    this.selection = fileChooser.showOpenDialog(stage);
    if (this.selection != null) {
      DefaultBackgroundUploadController.lastFolderSelection = this.selection.getParentFile();
      this.fileNameField.setText(this.selection.getAbsolutePath());
    }
    else {
      this.fileNameField.setText("");
    }
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    this.result = false;
    this.selection = null;

    this.uploadBtn.setDisable(true);
    this.fileNameField.textProperty().addListener((observableValue, s, t1) -> uploadBtn.setDisable(StringUtils.isEmpty(t1)));
  }

  @Override
  public void onDialogCancel() {
    result = false;
  }

  public boolean uploadFinished() {
    return result;
  }

  public void setGame(GameRepresentation game) {
    this.game = game;
    this.titleLabel.setText("Select default background for \"" + game.getGameDisplayName() + "\":");
  }
}
