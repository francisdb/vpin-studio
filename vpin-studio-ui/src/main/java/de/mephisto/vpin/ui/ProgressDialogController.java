package de.mephisto.vpin.ui;

import de.mephisto.vpin.commons.fx.DialogController;
import de.mephisto.vpin.commons.utils.WidgetFactory;
import de.mephisto.vpin.ui.util.ProgressModel;
import de.mephisto.vpin.ui.util.ProgressResultModel;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class ProgressDialogController implements Initializable, DialogController {

  @FXML
  private Label titleLabel;

  @FXML
  private Label progressBarLabel;

  @FXML
  private ProgressBar progressBar;

  @FXML
  private Button cancelButton;

  private Service service;
  private ProgressResultModel progressResultModel;
  private ProgressModel model;

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    cancelButton.setOnAction(event -> service.cancel());
  }

  public void setProgressModel(Stage stage, ProgressModel model) {
    this.model = model;
    titleLabel.setText(model.getTitle());

    if (model.isIndeterminate()) {
      progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    }


    progressResultModel = new ProgressResultModel(progressBar);
    progressBarLabel.setText("");
    service = new Service() {
      @Override
      protected Task createTask() {
        return new Task() {
          @Override
          protected Object call() throws Exception {
            int index = 1;

            try {
              while (model.hasNext() && !this.isCancelled()) {
                Object next = model.getNext();
                final int uiIndex = index;

                Platform.runLater(() -> {
                  String label = model.nextToString(next);
                  if (label != null) {
                    progressBarLabel.setText("Status: " + model.nextToString(next));
                  }
                  String title = model.getTitle();
                  if (model.getMax() > 1) {
                    title = title + " (" + uiIndex + "/" + model.getMax() + ")";
                  }
                  titleLabel.setText(title);
                });

                model.processNext(progressResultModel, next);
                if (!model.isIndeterminate()) {
                  long percent = index * 100 / model.getMax();
                  updateProgress(percent, 100);
                }

                Platform.runLater(() -> {
                  if (!model.isIndeterminate()) {
                    double p = Double.valueOf(uiIndex) / model.getMax();
                    progressBar.setProgress(p);
                  }
                });
                index++;
              }

              Platform.runLater(() -> {
                stage.close();
                if (model.isShowSummary()) {
                  Platform.runLater(() -> {
                    String msg = model.getTitle() + " finished.";
                    WidgetFactory.showInformation(Studio.stage, msg, "Processed " + progressResultModel.getProcessed() + " of " + model.getMax() + " elements.");
                  });
                }
              });
            } catch (Exception e) {
              Platform.runLater(() -> {
                stage.close();
              });
              Platform.runLater(() -> {
                WidgetFactory.showAlert(Studio.stage, "Error", e.getMessage());
              });
            }
            return null;
          }
        };
      }
    };
    service.start();
  }

  @Override
  public void onDialogCancel() {
    service.cancel();

    if(this.model != null) {
      model.cancel();
    }
  }

  public ProgressResultModel getProgressResult() {
    return this.progressResultModel;
  }
}
