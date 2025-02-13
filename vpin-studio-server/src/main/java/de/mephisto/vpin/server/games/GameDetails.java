package de.mephisto.vpin.server.games;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "GameDetails")
@EntityListeners(AuditingEntityListener.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameDetails {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(nullable = false, updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  @CreatedDate
  private Date createdAt;

  @Column(nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  @LastModifiedDate
  private Date updatedAt;

  @Column(length = 4096)
  public String assets;

  private String romName;

  private String tableName;

  private String extTableId;

  private String extTableVersionId;

  private String hsFileName;

  private String ignoredValidations;

  private int pupId;

  private int nvOffset;

  public String getExtTableId() {
    return extTableId;
  }

  public void setExtTableId(String extTableId) {
    this.extTableId = extTableId;
  }

  public String getExtTableVersionId() {
    return extTableVersionId;
  }

  public void setExtTableVersionId(String extTableVersionId) {
    this.extTableVersionId = extTableVersionId;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getAssets() {
    return assets;
  }

  public void setAssets(String assets) {
    this.assets = assets;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  public Date getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt;
  }

  public int getPupId() {
    return pupId;
  }

  public void setPupId(int pupId) {
    this.pupId = pupId;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getIgnoredValidations() {
    return ignoredValidations;
  }

  public void setIgnoredValidations(String ignoredValidations) {
    this.ignoredValidations = ignoredValidations;
  }

  @Nullable
  public String getHsFileName() {
    return hsFileName;
  }

  public void setHsFileName(@Nullable String hsFileName) {
    this.hsFileName = hsFileName;
  }

  @Nullable
  public String getRomName() {
    return romName;
  }

  public void setRomName(@Nullable String romName) {
    this.romName = romName;
  }

  public int getNvOffset() {
    return nvOffset;
  }

  public void setNvOffset(int nvOffset) {
    this.nvOffset = nvOffset;
  }
}
