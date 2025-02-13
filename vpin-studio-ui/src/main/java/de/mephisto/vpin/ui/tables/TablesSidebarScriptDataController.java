package de.mephisto.vpin.ui.tables;

import de.mephisto.vpin.commons.utils.FileUtils;
import de.mephisto.vpin.commons.utils.WidgetFactory;
import de.mephisto.vpin.restclient.SystemSummary;
import de.mephisto.vpin.restclient.representations.GameRepresentation;
import de.mephisto.vpin.restclient.representations.ValidationState;
import de.mephisto.vpin.ui.Studio;
import de.mephisto.vpin.ui.events.EventManager;
import de.mephisto.vpin.ui.tables.dialogs.ScriptDownloadProgressModel;
import de.mephisto.vpin.ui.tables.validation.LocalizedValidation;
import de.mephisto.vpin.ui.tables.validation.ValidationTexts;
import de.mephisto.vpin.ui.util.Dialogs;
import de.mephisto.vpin.ui.util.ProgressResultModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class TablesSidebarScriptDataController implements Initializable {
  private final static Logger LOG = LoggerFactory.getLogger(TablesSidebarScriptDataController.class);

  @FXML
  private Label labelRom;

  @FXML
  private Label labelRomAlias;

  @FXML
  private Label labelNVOffset;

  @FXML
  private Label labelFilename;

  @FXML
  private Label labelFilesize;

  @FXML
  private Label labelHSFilename;

  @FXML
  private Label labelTableName;

  @FXML
  private Label labelLastModified;

  @FXML
  private Button editHsFileNameBtn;

  @FXML
  private Button editRomNameBtn;

  @FXML
  private Button romUploadBtn;

  @FXML
  private Button inspectBtn;

  @FXML
  private Button editBtn;

  @FXML
  private Button scanBtn;

  @FXML
  private Button editAliasBtn;

  @FXML
  private Button deleteAliasBtn;

  @FXML
  private Button editTableNameBtn;

  @FXML
  private Button vpSaveEditBtn;

  @FXML
  private Button openTablesFolderBtn;

  @FXML
  private Button renameBtn;

  @FXML
  private VBox errorBox;

  @FXML
  private Label errorTitle;

  @FXML
  private Label errorText;

  private Optional<GameRepresentation> game = Optional.empty();

  private TablesSidebarController tablesSidebarController;

  private ValidationState validationState;

  // Add a public no-args constructor
  public TablesSidebarScriptDataController() {
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    errorBox.managedProperty().bindBidirectional(errorBox.visibleProperty());
    vpSaveEditBtn.setDisable(!Studio.client.getSystemService().isLocal());
  }

  public void setGame(Optional<GameRepresentation> game) {
    this.game = game;
    this.refreshView(game);
  }

  @FXML
  private void onTablesFolderOpen() {
    try {
      SystemSummary systemSummary = Studio.client.getSystemService().getSystemSummary();
      new ProcessBuilder("explorer.exe", new File(systemSummary.getVisualPinballDirectory(), "tables").getAbsolutePath()).start();
    } catch (Exception e) {
      LOG.error("Failed to open Explorer: " + e.getMessage(), e);
    }
  }

  @FXML
  private void onRename() {
    tablesSidebarController.getTablesController().onAssetsRename();
  }

  @FXML
  private void onHsFileNameEdit() {
    GameRepresentation gameRepresentation = game.get();
    String fs = WidgetFactory.showInputDialog(Studio.stage, "EM Highscore Filename", "Enter EM Highscore Filename",
        "Enter the name of the highscore file for this table.", "If available, the file is located in the 'VisualPinball\\User' folder.", gameRepresentation.getHsFileName());
    if (fs != null) {
      gameRepresentation.setHsFileName(fs);

      try {
        Studio.client.getGameService().saveGame(gameRepresentation);
        this.tablesSidebarController.getTablesSidebarHighscoresController().refreshView(game, true);
      } catch (Exception e) {
        WidgetFactory.showAlert(Studio.stage, e.getMessage());
      }
      EventManager.getInstance().notifyTableChange(gameRepresentation.getId(), null);
    }
  }

  @FXML
  public void onAliasEdit() {
    if (this.game.isPresent()) {
      GameRepresentation g = this.game.get();
      String rom = g.getRom();
      String alias = g.getRomAlias();
      Dialogs.openAliasMappingDialog(g, alias, rom);
    }
  }


  @FXML
  public void onDeleteAlias() {
    if (this.game.isPresent()) {
      GameRepresentation g = this.game.get();
      String alias = g.getRomAlias();

      Optional<ButtonType> result = WidgetFactory.showConfirmation(Studio.stage, "Delete Alias", "Delete alias \"" + alias + "\" for ROM \"" + g.getRom() + "\"?");
      if (result.isPresent() && result.get().equals(ButtonType.OK)) {
        Studio.client.getRomService().deleteAliasMapping(alias);
        EventManager.getInstance().notifyTableChange(g.getId(), g.getRom());
      }
    }
  }


  @FXML
  public void onEdit() {
    if (this.game.isPresent()) {
      tablesSidebarController.getTablesController().showEditor(this.game.get());
    }
  }

  @FXML
  public void onScan() {
    if (this.game.isPresent()) {
      Dialogs.createProgressDialog(new TableScanProgressModel("Scanning Table \"" + this.game.get().getGameDisplayName() + "\"", Arrays.asList(this.game.get())));
      EventManager.getInstance().notifyTableChange(this.game.get().getId(), null);
    }
  }

  @FXML
  public void onVPSaveEdit() {
    try {
      SystemSummary systemSummary = Studio.client.getSystemService().getSystemSummary();
      ProcessBuilder builder = new ProcessBuilder(new File("resources", "VPSaveEdit.exe").getAbsolutePath());
      builder.directory(new File("resources"));
      builder.start();
    } catch (IOException e) {
      LOG.error("Failed to open VPSaveEdit: " + e.getMessage(), e);
      WidgetFactory.showAlert(Studio.stage, "Error", "Failed to open VPSaveEdit: " + e.getMessage());
    }
  }

  @FXML
  public void onRomUpload() {
    boolean uploaded = Dialogs.openRomUploadDialog();
    if (uploaded) {
      tablesSidebarController.getTablesController().onReload();
    }
  }

  @FXML
  private void onTableNameEdit() {
    GameRepresentation gameRepresentation = game.get();
    String tableName = WidgetFactory.showInputDialog(Studio.stage, "Table Name", "Enter Table Name",
        "Enter the value for the 'TableName' property.",
        "The value is configured for some tables and used during highscore extraction.",
        gameRepresentation.getTableName());
    if (tableName != null) {
      gameRepresentation.setTableName(tableName);
      try {
        Studio.client.getGameService().saveGame(gameRepresentation);
        tablesSidebarController.getTablesSidebarHighscoresController().refreshView(game, true);
      } catch (Exception e) {
        WidgetFactory.showAlert(Studio.stage, e.getMessage());
      }
      EventManager.getInstance().notifyTableChange(gameRepresentation.getId(), null);
    }
  }

  @FXML
  private void onRomEdit() {
    GameRepresentation gameRepresentation = game.get();
    String romName = WidgetFactory.showInputDialog(Studio.stage, "ROM Name", "ROM Name", "The ROM name will be used for highscore and PUP pack resolving.", "Open the VPX table script editor to search for the ROM name.", gameRepresentation.getRom());
    if (romName != null) {
      gameRepresentation.setRom(romName);
      try {
        Studio.client.getGameService().saveGame(gameRepresentation);
        this.tablesSidebarController.getTablesSidebarHighscoresController().refreshView(game, true);
      } catch (Exception e) {
        WidgetFactory.showAlert(Studio.stage, e.getMessage());
      }
      EventManager.getInstance().notifyTableChange(gameRepresentation.getId(), null);
    }
  }

  @FXML
  private void onInspect() {
    if (game.isPresent()) {
      Optional<ButtonType> result = WidgetFactory.showConfirmation(Studio.stage, "Inspect script of table\"" + game.get().getGameDisplayName() + "\"?",
          "This will extract the table script into a temporary file.",
          "It will be opened afterwards in a text editor.");
      if (result.isPresent() && result.get().equals(ButtonType.OK)) {

        ProgressResultModel resultModel = Dialogs.createProgressDialog(new ScriptDownloadProgressModel("Extracting Table Script", game.get()));
        if (!resultModel.getResults().isEmpty()) {
          File file = (File) resultModel.getResults().get(0);
          try {
            Desktop.getDesktop().open(file);
          } catch (IOException e) {
            WidgetFactory.showAlert(Studio.stage, "Failed to open script file " + file.getAbsolutePath() + ": " + e.getMessage());
          }
        }
        else {
          WidgetFactory.showAlert(Studio.stage, "Script extraction failed, check log for details.");
        }
      }
    }
  }

  @FXML
  private void onDismiss() {
    GameRepresentation g = game.get();
    tablesSidebarController.getTablesController().dismissValidation(g, this.validationState);
  }

  public void refreshView(Optional<GameRepresentation> g) {
    errorBox.setVisible(false);

    editHsFileNameBtn.setDisable(g.isEmpty());
    editRomNameBtn.setDisable(g.isEmpty());
    editTableNameBtn.setDisable(g.isEmpty());
    romUploadBtn.setDisable(g.isEmpty());
    renameBtn.setDisable(g.isEmpty() || !g.get().isGameFileAvailable());
    openTablesFolderBtn.setVisible(Studio.client.getSystemService().isLocal());

    inspectBtn.setDisable(g.isEmpty() || !g.get().isGameFileAvailable());
    editBtn.setDisable(g.isEmpty() || !g.get().isGameFileAvailable());
    scanBtn.setDisable(g.isEmpty() || !g.get().isGameFileAvailable());
    editAliasBtn.setDisable(g.isEmpty() || !g.get().isGameFileAvailable());
    deleteAliasBtn.setDisable(g.isEmpty() || !g.get().isGameFileAvailable());

    if (g.isPresent()) {
      GameRepresentation game = g.get();

      editHsFileNameBtn.setDisable(!game.getEmulator().isVisualPinball());
      editRomNameBtn.setDisable(!game.getEmulator().isVisualPinball());
      editTableNameBtn.setDisable(!game.getEmulator().isVisualPinball());
      romUploadBtn.setDisable(!game.getEmulator().isVisualPinball());
      deleteAliasBtn.setDisable(StringUtils.isEmpty(game.getRomAlias()));

      labelRom.setText(!StringUtils.isEmpty(game.getRom()) ? game.getRom() : "-");
      labelRomAlias.setText(!StringUtils.isEmpty(game.getRomAlias()) ? game.getRomAlias() : "-");
      labelNVOffset.setText(game.getNvOffset() > 0 ? String.valueOf(game.getNvOffset()) : "-");
      labelFilename.setText(game.getGameFileName() != null ? game.getGameFileName() : "-");
      labelFilesize.setText(game.getGameFileSize() > 0 ? FileUtils.readableFileSize(game.getGameFileSize()) : "-");
      labelTableName.setText(!StringUtils.isEmpty(game.getTableName()) ? game.getTableName() : "-");
      labelLastModified.setText(game.getModified() != null ? DateFormat.getDateTimeInstance().format(game.getModified()) : "-");
      if (!StringUtils.isEmpty(game.getHsFileName())) {
        labelHSFilename.setText(game.getHsFileName());
      }
      else {
        labelHSFilename.setText("-");
      }

      List<ValidationState> validationStates = Studio.client.getGameService().getRomValidations(game.getId());
      errorBox.setVisible(!validationStates.isEmpty());
      if (!validationStates.isEmpty()) {
        validationState = validationStates.get(0);
        LocalizedValidation validationResult = ValidationTexts.getValidationResult(game, validationState);
        errorTitle.setText(validationResult.getLabel());
        errorText.setText(validationResult.getText());
      }
    }
    else {
      labelRom.setText("-");
      labelRomAlias.setText("-");
      labelNVOffset.setText("-");
      labelFilename.setText("-");
      labelFilesize.setText("-");
      labelLastModified.setText("-");
      labelTableName.setText("-");
      labelHSFilename.setText("-");
    }
  }

  public void setSidebarController(TablesSidebarController tablesSidebarController) {
    this.tablesSidebarController = tablesSidebarController;
  }

}