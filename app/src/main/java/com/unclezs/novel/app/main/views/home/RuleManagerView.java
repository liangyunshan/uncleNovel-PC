package com.unclezs.novel.app.main.views.home;

import cn.hutool.core.io.FileUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.unclezs.novel.analyzer.core.helper.RuleHelper;
import com.unclezs.novel.analyzer.core.model.AnalyzerRule;
import com.unclezs.novel.analyzer.request.Http;
import com.unclezs.novel.analyzer.request.RequestParams;
import com.unclezs.novel.analyzer.util.GsonUtils;
import com.unclezs.novel.analyzer.util.StringUtils;
import com.unclezs.novel.analyzer.util.uri.UrlUtils;
import com.unclezs.novel.app.framework.annotation.FxView;
import com.unclezs.novel.app.framework.components.ModalBox;
import com.unclezs.novel.app.framework.components.Toast;
import com.unclezs.novel.app.framework.components.icon.Icon;
import com.unclezs.novel.app.framework.components.icon.IconFont;
import com.unclezs.novel.app.framework.components.sidebar.SidebarNavigateBundle;
import com.unclezs.novel.app.framework.components.sidebar.SidebarView;
import com.unclezs.novel.app.framework.executor.TaskFactory;
import com.unclezs.novel.app.framework.util.DesktopUtils;
import com.unclezs.novel.app.framework.util.NodeHelper;
import com.unclezs.novel.app.main.App;
import com.unclezs.novel.app.main.manager.RuleManager;
import com.unclezs.novel.app.main.util.MixPanelHelper;
import com.unclezs.novel.app.main.views.components.cell.ActionButtonTableCell;
import com.unclezs.novel.app.main.views.components.cell.CheckBoxTableCell;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ????????????
 *
 * @author blog.unclezs.com
 * @date 2021/4/20 11:16
 */
@Slf4j
@FxView(fxml = "/layout/home/rule-manager.fxml")
@EqualsAndHashCode(callSuper = true)
public class RuleManagerView extends SidebarView<StackPane> {

  /**
   * ??????????????????
   */
  public static final String RULES_FILE_NAME = "rules.json";
  private static final String PAGE_NAME = "????????????";
  @FXML
  private TableView<AnalyzerRule> rulesTable;
  /**
   * ??????????????????
   */
  private ContextMenu importRuleMenu;

  @Override
  public void onCreated() {
    rulesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    createRuleTableColumns();
    rulesTable.setItems(RuleManager.rules());
  }

  @Override
  public void onHidden() {
    RuleManager.update(rulesTable.getItems());
    RuleManager.save();
  }

  @Override
  public void onShow(SidebarNavigateBundle bundle) {
    MixPanelHelper.event(PAGE_NAME);
    AnalyzerRule rule = bundle.get(RuleEditorView.BUNDLE_RULE_KEY);
    // ????????????
    if (rule != null) {
      rulesTable.getItems().add(rule);
    }
    rulesTable.refresh();
  }

  @Override
  public void onDestroy() {
    RuleManager.save();
  }

  /**
   * ?????????????????????
   */
  @SuppressWarnings("unchecked")
  private void createRuleTableColumns() {
    // ??????
    TableColumn<AnalyzerRule, Integer> id = NodeHelper.addClass(new TableColumn<>("#"), "id");
    id.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.06));
    id.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(rulesTable.getItems().indexOf(col.getValue()) + 1));
    // ??????
    TableColumn<AnalyzerRule, String> name = new TableColumn<>(localized("rule.manager.table.name"));
    name.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.15));
    name.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getName()));
    // ??????
    TableColumn<AnalyzerRule, String> group = new TableColumn<>(localized("rule.manager.table.category"));
    group.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.08));
    group.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getGroup()));
    // ??????
    TableColumn<AnalyzerRule, Integer> weight = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.weight")), "align-center");
    weight.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.1));
    weight.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getWeight()));
    // ??????
    TableColumn<AnalyzerRule, String> site = new TableColumn<>(localized("rule.manager.table.site"));
    site.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.25));
    site.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getSite()));
    // ????????????
    TableColumn<AnalyzerRule, String> type = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.type")));
    type.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.1));
    type.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(Boolean.TRUE.equals(col.getValue().getAudio()) ? "??????" : "??????"));
    // ????????????
    TableColumn<AnalyzerRule, Boolean> enabled = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.enabled")), "align-center");
    enabled.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.1));
    enabled.setCellValueFactory(col -> new ReadOnlyBooleanWrapper(Boolean.TRUE.equals(col.getValue().getEnabled())));
    enabled.setCellFactory(col -> new CheckBoxTableCell<>(this::onEnabledChange));
    // ??????
    TableColumn<AnalyzerRule, AnalyzerRule> operation = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.operation")), "align-center");
    operation.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.13));
    operation.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue()));
    operation.setCellFactory(col -> new ActionButtonTableCell<>(this::onEdit, this::onDelete));
    // ?????????
    rulesTable.getColumns().addAll(id, name, group, weight, site, type, enabled, operation);
    // ??????resize
    rulesTable.getColumns().forEach(column -> column.setResizable(false));
    rulesTable.setOnContextMenuRequested(event -> {
      if (rulesTable.getSelectionModel().isEmpty()) {
        rulesTable.getContextMenu().hide();
      }
    });
  }

  /**
   * ????????????
   *
   * @param rule  ??????
   * @param index ??????
   */
  private void onDelete(AnalyzerRule rule, int index) {
    ModalBox.confirm(delete -> {
      if (Boolean.TRUE.equals(delete)) {
        rulesTable.getItems().remove(index);
      }
    }).title("??????????????????")
      .message(String.format("?????????????????????%s", rule.getName()))
      .show();
  }

  /**
   * ????????????
   *
   * @param rule  ??????
   * @param index ?????????
   */
  private void onEdit(AnalyzerRule rule, int index) {
    SidebarNavigateBundle bundle = new SidebarNavigateBundle();
    bundle.put(RuleEditorView.BUNDLE_RULE_KEY, rule);
    navigation.navigate(RuleEditorView.class, bundle);
  }

  /**
   * ??????????????????
   *
   * @param enabled ????????????
   * @param index   ?????????
   */
  private void onEnabledChange(boolean enabled, int index) {
    rulesTable.getItems().get(index).setEnabled(enabled);
  }

  /**
   * ????????????
   */
  @FXML
  private void disabledSelected() {
    rulesTable.getSelectionModel().getSelectedItems().forEach(rule -> rule.setEnabled(false));
    rulesTable.refresh();
  }

  /**
   * ????????????
   */
  @FXML
  private void enabledSelected() {
    rulesTable.getSelectionModel().getSelectedItems().forEach(rule -> rule.setEnabled(true));
    rulesTable.refresh();
  }

  /**
   * ????????????
   */
  @FXML
  private void exportSelected() {
    exportRule(new ArrayList<>(rulesTable.getSelectionModel().getSelectedItems()));
  }

  /**
   * ????????????
   */
  @FXML
  private void exportAll() {
    exportRule(new ArrayList<>(rulesTable.getItems()));
  }

  /**
   * ??????????????????????????????
   */
  @FXML
  private void copySelectedRule() {
    ArrayList<AnalyzerRule> selected = new ArrayList<>(rulesTable.getSelectionModel().getSelectedItems());
    DesktopUtils.copy(RuleHelper.GSON.toJson(selected));
    Toast.success("????????????");
  }

  /**
   * ????????????????????????
   */
  @FXML
  private void importRule(MouseEvent event) {
    if (importRuleMenu == null) {
      importRuleMenu = new ContextMenu();
      importRuleMenu.setHideOnEscape(true);
      MenuItem file = new MenuItem("????????????", new Icon(IconFont.FOLDER));
      file.setOnAction(e -> this.importRuleFromFile());
      MenuItem url = new MenuItem("????????????", new Icon(IconFont.BROWSER));
      url.setOnAction(e -> this.importRuleFromUrl());
      MenuItem clipboard = new MenuItem("???????????????", new Icon(IconFont.COPY));
      clipboard.setOnAction(e -> this.importRule(Clipboard.getSystemClipboard().getString()));
      importRuleMenu.getItems().setAll(file, url, clipboard);
    }
    importRuleMenu.show(App.stage(), event.getScreenX(), event.getScreenY());
  }

  /**
   * ???URL????????????
   */
  public void importRuleFromUrl() {
    ModalBox.input("?????????????????????", this::importRuleFromUrl)
      .title("???????????? - ?????????????????????")
      .show();
  }

  /**
   * ???URL????????????
   */
  public void importRuleFromFile() {
    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("JSON", "*.json");
    fileChooser.getExtensionFilters().add(filter);
    File file = fileChooser.showOpenDialog(App.stage());
    importRule(file);
  }


  /**
   * ???????????????????????????
   *
   * @param file ??????
   */
  public void importRule(File file) {
    if (FileUtil.exist(file)) {
      importRule(FileUtil.readUtf8String(file));
    }
  }

  /**
   * ???URL????????????
   *
   * @param url ????????????
   */
  public void importRuleFromUrl(String url) {
    if (!UrlUtils.isHttpUrl(url)) {
      Toast.error("??????????????????????????????");
    }
    TaskFactory.create(() -> {
      RequestParams params = RequestParams.create(url);
      params.setCharset(StandardCharsets.UTF_8.name());
      return Http.content(params);
    })
      .onSuccess(this::importRule)
      .start();
  }


  /**
   * ????????????
   *
   * @param ruleJson ??????JSON
   */
  public void importRule(String ruleJson) {
    if (StringUtils.isBlank(ruleJson)) {
      Toast.error("???????????????????????????");
      return;
    }
    try {
      Set<String> ruleSites = rulesTable.getItems().stream().map(rule -> UrlUtils.getHost(rule.getSite())).collect(Collectors.toSet());
      JsonElement element = JsonParser.parseString(ruleJson);
      if (element.isJsonArray()) {
        List<AnalyzerRule> rules = RuleHelper.parseRules(ruleJson, AnalyzerRule.class);
        rules.stream()
          .filter(rule -> rule.isEffective() && !ruleSites.contains(UrlUtils.getHost(rule.getSite())))
          .forEach(rule -> rulesTable.getItems().add(rule));
      } else {
        AnalyzerRule rule = RuleHelper.parseRule(ruleJson, AnalyzerRule.class);
        RuleManager.addRule(rule);
      }
      Toast.success("????????????");
      rulesTable.refresh();
      RuleManager.save();
    } catch (Exception e) {
      log.error("??????????????????", e);
      Toast.error("???????????????????????????");
    }
  }


  /**
   * ????????????
   *
   * @param rules ????????????
   */
  private void exportRule(List<AnalyzerRule> rules) {
    String ruleJson = RuleHelper.GSON.toJson(rules);
    FileChooser fileChooser = new FileChooser();
    fileChooser.setInitialFileName(RULES_FILE_NAME);
    File file = fileChooser.showSaveDialog(App.stage());
    if (file != null) {
      FileUtil.writeUtf8String(ruleJson, file);
      Toast.success(getRoot(), "????????????");
    }
  }

  /**
   * ????????????
   */
  @FXML
  private void deleteSelected() {
    ObservableList<AnalyzerRule> rules = rulesTable.getSelectionModel().getSelectedItems();
    ModalBox.confirm(delete -> {
      if (Boolean.TRUE.equals(delete)) {
        rulesTable.getItems().removeAll(rules);
        Toast.success(getRoot(), "????????????");
      }
    }).title("??????????????????")
      .message(String.format("?????????????????????%d??????????", rules.size()))
      .show();
  }

  /**
   * ????????????
   */
  @FXML
  private void addRule() {
    navigation.navigate(RuleEditorView.class);
  }
}
