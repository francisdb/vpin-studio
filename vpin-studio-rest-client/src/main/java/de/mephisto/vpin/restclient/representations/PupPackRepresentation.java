package de.mephisto.vpin.restclient.representations;

import de.mephisto.vpin.restclient.popper.ScreenMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PupPackRepresentation {
  private long size;
  private Date modificationDate;
  private boolean enabled;
  private List<String> options = new ArrayList<>();
  private List<String> txtFiles = new ArrayList<>();
  private String path;
  private List<ValidationState> validationStates;

  private ScreenMode screenBackglassMode;
  private ScreenMode screenDMDMode;
  private ScreenMode screenFullDMDMode;
  private ScreenMode screenTopperMode;

  private String selectedOption;

  public List<ValidationState> getValidationStates() {
    return validationStates;
  }

  public void setValidationStates(List<ValidationState> validationStates) {
    this.validationStates = validationStates;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public List<String> getTxtFiles() {
    return txtFiles;
  }

  public void setTxtFiles(List<String> txtFiles) {
    this.txtFiles = txtFiles;
  }

  public String getSelectedOption() {
    return selectedOption;
  }

  public void setSelectedOption(String selectedOption) {
    this.selectedOption = selectedOption;
  }

  private List<String> missingResources = new ArrayList<>();

  public List<String> getMissingResources() {
    return missingResources;
  }

  public void setMissingResources(List<String> missingResources) {
    this.missingResources = missingResources;
  }

  public ScreenMode getScreenBackglassMode() {
    return screenBackglassMode;
  }

  public void setScreenBackglassMode(ScreenMode screenBackglassMode) {
    this.screenBackglassMode = screenBackglassMode;
  }

  public ScreenMode getScreenDMDMode() {
    return screenDMDMode;
  }

  public void setScreenDMDMode(ScreenMode screenDMDMode) {
    this.screenDMDMode = screenDMDMode;
  }

  public ScreenMode getScreenFullDMDMode() {
    return screenFullDMDMode;
  }

  public void setScreenFullDMDMode(ScreenMode screenFullDMDMode) {
    this.screenFullDMDMode = screenFullDMDMode;
  }

  public ScreenMode getScreenTopperMode() {
    return screenTopperMode;
  }

  public void setScreenTopperMode(ScreenMode screenTopperMode) {
    this.screenTopperMode = screenTopperMode;
  }

  public List<String> getOptions() {
    return options;
  }

  public void setOptions(List<String> options) {
    this.options = options;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Date getModificationDate() {
    return modificationDate;
  }

  public void setModificationDate(Date modificationDate) {
    this.modificationDate = modificationDate;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }
}
