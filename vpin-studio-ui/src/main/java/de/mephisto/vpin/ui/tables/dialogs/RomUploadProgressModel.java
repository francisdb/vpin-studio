package de.mephisto.vpin.ui.tables.dialogs;

import de.mephisto.vpin.ui.Studio;
import de.mephisto.vpin.ui.util.ProgressModel;
import de.mephisto.vpin.ui.util.ProgressResultModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public class RomUploadProgressModel extends ProgressModel<File> {
  private final static Logger LOG = LoggerFactory.getLogger(RomUploadProgressModel.class);

  private final Iterator<File> iterator;
  private final List<File> files;
  private double percentage = 0;

  public RomUploadProgressModel(String title, List<File> files) {
    super(title);
    this.files = files;
    this.iterator = files.iterator();
  }

  @Override
  public boolean isShowSummary() {
    return false;
  }

  @Override
  public File getNext() {
    return iterator.next();
  }

  @Override
  public String nextToString(File file) {
    return file.getName();
  }

  @Override
  public int getMax() {
    return files.size();
  }

  @Override
  public void processNext(ProgressResultModel progressResultModel, File next) {
    try {
      Studio.client.getGameService().uploadRom(next, percent -> {
        double total = percentage + percent;
        progressResultModel.setProgress(total / this.files.size());
      });
      progressResultModel.addProcessed();
      percentage++;
    } catch (Exception e) {
      LOG.error("ROM upload failed: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }
}
