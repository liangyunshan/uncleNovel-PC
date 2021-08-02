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
 * 书源管理
 *
 * @author blog.unclezs.com
 * @date 2021/4/20 11:16
 */
@Slf4j
@FxView(fxml = "/layout/home/rule-manager.fxml")
@EqualsAndHashCode(callSuper = true)
public class RuleManagerView extends SidebarView<StackPane> {

  /**
   * 书源的文件名
   */
  public static final String RULES_FILE_NAME = "rules.json";
  private static final String PAGE_NAME = "书源管理";
  @FXML
  private TableView<AnalyzerRule> rulesTable;
  /**
   * 导入书源菜单
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
    // 新增书源
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
   * 创建书源表格列
   */
  @SuppressWarnings("unchecked")
  private void createRuleTableColumns() {
    // 序号
    TableColumn<AnalyzerRule, Integer> id = NodeHelper.addClass(new TableColumn<>("#"), "id");
    id.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.06));
    id.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(rulesTable.getItems().indexOf(col.getValue()) + 1));
    // 名称
    TableColumn<AnalyzerRule, String> name = new TableColumn<>(localized("rule.manager.table.name"));
    name.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.15));
    name.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getName()));
    // 分组
    TableColumn<AnalyzerRule, String> group = new TableColumn<>(localized("rule.manager.table.category"));
    group.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.08));
    group.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getGroup()));
    // 权重
    TableColumn<AnalyzerRule, Integer> weight = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.weight")), "align-center");
    weight.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.1));
    weight.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getWeight()));
    // 站点
    TableColumn<AnalyzerRule, String> site = new TableColumn<>(localized("rule.manager.table.site"));
    site.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.25));
    site.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue().getSite()));
    // 书源类型
    TableColumn<AnalyzerRule, String> type = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.type")));
    type.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.1));
    type.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(Boolean.TRUE.equals(col.getValue().getAudio()) ? "有声" : "文字"));
    // 是否启用
    TableColumn<AnalyzerRule, Boolean> enabled = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.enabled")), "align-center");
    enabled.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.1));
    enabled.setCellValueFactory(col -> new ReadOnlyBooleanWrapper(Boolean.TRUE.equals(col.getValue().getEnabled())));
    enabled.setCellFactory(col -> new CheckBoxTableCell<>(this::onEnabledChange));
    // 操作
    TableColumn<AnalyzerRule, AnalyzerRule> operation = NodeHelper.addClass(new TableColumn<>(localized("rule.manager.table.operation")), "align-center");
    operation.prefWidthProperty().bind(rulesTable.widthProperty().multiply(0.13));
    operation.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue()));
    operation.setCellFactory(col -> new ActionButtonTableCell<>(this::onEdit, this::onDelete));
    // 添加列
    rulesTable.getColumns().addAll(id, name, group, weight, site, type, enabled, operation);
    // 禁用resize
    rulesTable.getColumns().forEach(column -> column.setResizable(false));
    rulesTable.setOnContextMenuRequested(event -> {
      if (rulesTable.getSelectionModel().isEmpty()) {
        rulesTable.getContextMenu().hide();
      }
    });
  }

  /**
   * 删除规则
   *
   * @param rule  规则
   * @param index 索引
   */
  private void onDelete(AnalyzerRule rule, int index) {
    ModalBox.confirm(delete -> {
      if (Boolean.TRUE.equals(delete)) {
        rulesTable.getItems().remove(index);
      }
    }).title("确定删除吗？")
      .message(String.format("是否删除规则：%s", rule.getName()))
      .show();
  }

  /**
   * 编辑规则
   *
   * @param rule  规则
   * @param index 当前行
   */
  private void onEdit(AnalyzerRule rule, int index) {
    SidebarNavigateBundle bundle = new SidebarNavigateBundle();
    bundle.put(RuleEditorView.BUNDLE_RULE_KEY, rule);
    navigation.navigate(RuleEditorView.class, bundle);
  }

  /**
   * 启用状态改变
   *
   * @param enabled 是否启用
   * @param index   当前行
   */
  private void onEnabledChange(boolean enabled, int index) {
    rulesTable.getItems().get(index).setEnabled(enabled);
  }

  /**
   * 禁用选中
   */
  @FXML
  private void disabledSelected() {
    rulesTable.getSelectionModel().getSelectedItems().forEach(rule -> rule.setEnabled(false));
    rulesTable.refresh();
  }

  /**
   * 启用选中
   */
  @FXML
  private void enabledSelected() {
    rulesTable.getSelectionModel().getSelectedItems().forEach(rule -> rule.setEnabled(true));
    rulesTable.refresh();
  }

  /**
   * 导出选中
   */
  @FXML
  private void exportSelected() {
    exportRule(new ArrayList<>(rulesTable.getSelectionModel().getSelectedItems()));
  }

  /**
   * 导出全部
   */
  @FXML
  private void exportAll() {
    exportRule(new ArrayList<>(rulesTable.getItems()));
  }

  /**
   * 复制选中规则到剪贴板
   */
  @FXML
  private void copySelectedRule() {
    ArrayList<AnalyzerRule> selected = new ArrayList<>(rulesTable.getSelectionModel().getSelectedItems());
    DesktopUtils.copy(RuleHelper.GSON.toJson(selected));
    Toast.success("复制成功");
  }

  /**
   * 导入规则，并去重
   */
  @FXML
  private void importRule(MouseEvent event) {
    if (importRuleMenu == null) {
      importRuleMenu = new ContextMenu();
      importRuleMenu.setHideOnEscape(true);
      MenuItem file = new MenuItem("本地导入", new Icon(IconFont.FOLDER));
      file.setOnAction(e -> this.importRuleFromFile());
      MenuItem url = new MenuItem("网络导入", new Icon(IconFont.BROWSER));
      url.setOnAction(e -> this.importRuleFromUrl());
      MenuItem clipboard = new MenuItem("剪贴板导入", new Icon(IconFont.COPY));
      clipboard.setOnAction(e -> this.importRule(Clipboard.getSystemClipboard().getString()));
      importRuleMenu.getItems().setAll(file, url, clipboard);
    }
    importRuleMenu.show(App.stage(), event.getScreenX(), event.getScreenY());
  }

  /**
   * 从URL导入书源
   */
  public void importRuleFromUrl() {
    ModalBox.input("请输入书源链接", this::importRuleFromUrl)
      .title("网络导入 - 请输入书源链接")
      .show();
  }

  /**
   * 从URL导入书源
   */
  public void importRuleFromFile() {
    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("JSON", "*.json");
    fileChooser.getExtensionFilters().add(filter);
    File file = fileChooser.showOpenDialog(App.stage());
    importRule(file);
  }


  /**
   * 从本地文件导入书源
   *
   * @param file 文件
   */
  public void importRule(File file) {
    if (FileUtil.exist(file)) {
      importRule(FileUtil.readUtf8String(file));
    }
  }

  /**
   * 从URL导入书源
   *
   * @param url 书源链接
   */
  public void importRuleFromUrl(String url) {
    if (!UrlUtils.isHttpUrl(url)) {
      Toast.error("请输入正确的书源链接");
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
   * 导入规则
   *
   * @param ruleJson 规则JSON
   */
  public void importRule(String ruleJson) {
    if (StringUtils.isBlank(ruleJson)) {
      Toast.error("导入失败，格式错误");
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
      Toast.success("导入成功");
      rulesTable.refresh();
      RuleManager.save();
    } catch (Exception e) {
      log.error("书源导入失败", e);
      Toast.error("导入失败，格式错误");
    }
  }


  /**
   * 导出书源
   *
   * @param rules 规则列表
   */
  private void exportRule(List<AnalyzerRule> rules) {
    String ruleJson = RuleHelper.GSON.toJson(rules);
    FileChooser fileChooser = new FileChooser();
    fileChooser.setInitialFileName(RULES_FILE_NAME);
    File file = fileChooser.showSaveDialog(App.stage());
    if (file != null) {
      FileUtil.writeUtf8String(ruleJson, file);
      Toast.success(getRoot(), "导出成功");
    }
  }

  /**
   * 删除选中
   */
  @FXML
  private void deleteSelected() {
    ObservableList<AnalyzerRule> rules = rulesTable.getSelectionModel().getSelectedItems();
    ModalBox.confirm(delete -> {
      if (Boolean.TRUE.equals(delete)) {
        rulesTable.getItems().removeAll(rules);
        Toast.success(getRoot(), "删除成功");
      }
    }).title("确定删除吗？")
      .message(String.format("是否删除选中的%d条规则?", rules.size()))
      .show();
  }

  /**
   * 新增规则
   */
  @FXML
  private void addRule() {
    navigation.navigate(RuleEditorView.class);
  }
}
