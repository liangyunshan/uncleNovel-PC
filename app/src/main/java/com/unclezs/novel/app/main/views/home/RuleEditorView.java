package com.unclezs.novel.app.main.views.home;

import cn.hutool.core.bean.BeanPath;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import com.google.gson.JsonSyntaxException;
import com.jfoenix.controls.JFXCheckBox;
import com.unclezs.novel.analyzer.core.helper.RuleHelper;
import com.unclezs.novel.analyzer.core.helper.RuleTester;
import com.unclezs.novel.analyzer.core.model.AnalyzerRule;
import com.unclezs.novel.analyzer.core.rule.CommonRule;
import com.unclezs.novel.analyzer.core.rule.RuleConstant;
import com.unclezs.novel.analyzer.request.RequestParams;
import com.unclezs.novel.analyzer.util.CollectionUtils;
import com.unclezs.novel.analyzer.util.GsonUtils;
import com.unclezs.novel.analyzer.util.StringUtils;
import com.unclezs.novel.analyzer.util.uri.UrlUtils;
import com.unclezs.novel.app.framework.annotation.FxView;
import com.unclezs.novel.app.framework.components.InputBox;
import com.unclezs.novel.app.framework.components.ModalBox;
import com.unclezs.novel.app.framework.components.Toast;
import com.unclezs.novel.app.framework.components.icon.IconButton;
import com.unclezs.novel.app.framework.components.icon.IconFont;
import com.unclezs.novel.app.framework.components.sidebar.SidebarNavigateBundle;
import com.unclezs.novel.app.framework.components.sidebar.SidebarView;
import com.unclezs.novel.app.framework.core.AppContext;
import com.unclezs.novel.app.framework.executor.Executor;
import com.unclezs.novel.app.framework.executor.FluentTask;
import com.unclezs.novel.app.framework.executor.TaskFactory;
import com.unclezs.novel.app.framework.util.DesktopUtils;
import com.unclezs.novel.app.framework.util.NodeHelper;
import com.unclezs.novel.app.main.App;
import com.unclezs.novel.app.main.manager.RuleManager;
import com.unclezs.novel.app.main.util.MixPanelHelper;
import com.unclezs.novel.app.main.views.components.rule.CommonRuleEditor;
import com.unclezs.novel.app.main.views.components.rule.ParamsEditor;
import com.unclezs.novel.app.main.views.components.rule.RuleItem;
import com.unclezs.novel.app.main.views.components.rule.RuleItems;
import com.unclezs.novel.app.main.views.components.rule.ScriptDebugBox;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ??????????????????
 *
 * @author blog.unclezs.com
 * @date 2021/4/21 12:05
 */
@Slf4j
@FxView(fxml = "/layout/home/rule-editor.fxml")
public class RuleEditorView extends SidebarView<StackPane> {

  public static final String BUNDLE_RULE_KEY = "rule";
  private static final String SELECTOR_CHECK_BOX = ".check-box";
  private static final String SELECTOR_COMBO_BOX = ".combo-box";
  private static final String SELECTOR_TEXT_INPUT = ".item > .text-input";
  private static final String SELECTOR_INPUT_BOX = ".input-box";
  private static final String PAGE_NAME = "????????????";
  /**
   * ?????????
   */
  private final Map<ReadOnlyProperty<?>, InvalidationListener> listeners = new HashMap<>();
  /**
   * ????????????
   */
  private final List<InputBox> inputBoxes = new ArrayList<>();
  private final List<TextInputControl> inputs = new ArrayList<>();
  private final List<CheckBox> checkBoxes = new ArrayList<>();
  private final List<ComboBox<String>> comboBoxes = new ArrayList<>();
  @FXML
  private TextField cookieField;
  /**
   * ???????????????????????????
   */
  @FXML
  private InputBox contentRule;
  /**
   * ??????????????????????????????
   */
  @FXML
  private ComboBox<String> autoAnalysisMode;
  @FXML
  private RuleItems infoItemsPanel;
  @FXML
  private TextArea sourceEditor;
  @FXML
  private ScrollPane panel;
  @FXML
  private IconButton showSourceButton;
  @FXML
  private VBox ruleContainer;
  @FXML
  private TextField weight;
  @FXML
  private TextField delayTime;
  private CommonRuleEditor editor;
  private VBox debugContentPanel;
  private VBox debugTocPanel;
  private VBox debugDetailPanel;
  private VBox debugSearchPanel;
  private VBox debugPanel;
  private VBox debugScriptPanel;
  /**
   * ??????????????????
   */
  private AnalyzerRule rule;
  /**
   * ??????????????????
   */
  private AnalyzerRule realRule;
  /**
   * ??????????????????????????????
   */
  private boolean fromManager = false;
  /**
   * ??????????????????
   */
  private SidebarView<?> from;
  /**
   * ?????????????????????????????? ????????????????????????
   */
  private RuleItem saveToRule;
  /**
   * ????????????????????????
   */
  private JFXCheckBox saveToRulesSwitch;

  /**
   * ???????????????????????????
   */
  @Override
  @SuppressWarnings("unchecked")
  public void onCreate() {
    ruleContainer.lookupAll(SELECTOR_TEXT_INPUT).stream().filter(node -> node.getUserData() != null).forEach(node -> inputs.add((TextInputControl) node));
    ruleContainer.lookupAll(SELECTOR_INPUT_BOX).stream().filter(node -> node.getUserData() != null).forEach(node -> inputBoxes.add((InputBox) node));
    ruleContainer.lookupAll(SELECTOR_CHECK_BOX).stream().filter(node -> node.getUserData() != null).forEach(node -> checkBoxes.add((CheckBox) node));
    ruleContainer.lookupAll(SELECTOR_COMBO_BOX).stream().filter(node -> node.getUserData() != null).forEach(node -> comboBoxes.add((ComboBox<String>) node));

    autoAnalysisMode.valueProperty().addListener(e -> {
      int mode = autoAnalysisMode.getItems().indexOf(autoAnalysisMode.getValue()) + 1;
      contentRule.getInput().setText(RuleConstant.TYPE_AUTO + StringUtils.COLON + mode);
      rule.getContent().setContent(CommonRule.create(RuleConstant.TYPE_AUTO, String.valueOf(mode)));
    });
  }

  /**
   * ??????????????????????????????????????????????????????
   *
   * @param bundle ??????????????????
   */
  @Override
  public void onShow(SidebarNavigateBundle bundle) {
    MixPanelHelper.event(PAGE_NAME);
    // ????????????????????????
    if (isSourceMode()) {
      sourceEditor.setText(null);
      showSource();
    }
    reset();
    this.fromManager = bundle.getFrom().equals(RuleManagerView.class.getName());
    this.from = AppContext.getView(bundle.getFrom());
    realRule = bundle.get(BUNDLE_RULE_KEY);
    if (realRule == null) {
      rule = new AnalyzerRule();
    } else {
      rule = realRule.copy();
    }
    addSaveToRuleItem();
    bindData();
    // ???????????????????????????????????????????????????
    if (realRule != null && !Objects.equals(rule, realRule)) {
      BeanUtil.copyProperties(rule, realRule);
    }
  }

  @Override
  public void onHidden() {
    this.reset();
  }

  private void addSaveToRuleItem() {
    if (saveToRule != null) {
      infoItemsPanel.removeItem(saveToRule);
    }
    if (!fromManager && !RuleManager.exist(rule)) {
      if (saveToRule == null) {
        saveToRule = new RuleItem();
        saveToRule.setName("???????????????");
        saveToRulesSwitch = new JFXCheckBox();
        saveToRulesSwitch.setSelected(true);
        saveToRule.getChildren().add(saveToRulesSwitch);
      }
      infoItemsPanel.addItem(0, saveToRule);
    }
  }

  /**
   * ?????????????????????
   */
  private void reset() {
    // ????????????
    listeners.forEach(Observable::removeListener);
    listeners.clear();
    // ?????????????????????
    inputBoxes.forEach(box -> box.getInput().setText(null));
    inputs.forEach(input -> input.setText(null));
    checkBoxes.forEach(box -> box.setSelected(false));
    comboBoxes.forEach(box -> box.setValue(null));
    debugContentPanel = null;
    debugDetailPanel = null;
    debugSearchPanel = null;
    debugTocPanel = null;
    debugPanel = null;
  }

  /**
   * ?????????????????????Cookie
   */
  @FXML
  private void getCookie() {
    WebView webView = new WebView();
    webView.setMaxHeight(400);
    webView.getEngine().load(rule.getSite());
    ModalBox.confirm(ok -> {
      if (Boolean.TRUE.equals(ok)) {
        try {
          Map<String, List<String>> map = CookieHandler.getDefault().get(URI.create(UrlUtils.getSite(rule.getSite())), Collections.emptyMap());
          List<String> cookie = map.get("Cookie");
          if (CollectionUtils.isNotEmpty(cookie)) {
            cookieField.setText(cookie.get(0));
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }).body(webView).title("??????????????????????????????cookie").show();
  }

  /**
   * ??????????????????
   */
  @FXML
  private void openSite() {
    if (!UrlUtils.isHttpUrl(rule.getSite())) {
      Toast.error("?????????????????????????????????~");
      return;
    }
    DesktopUtils.openBrowse(rule.getSite());
  }

  /**
   * ??????????????????
   *
   * @param event ????????????
   */
  @FXML
  private void editParams(MouseEvent event) {
    Node source = (Node) event.getSource();
    RequestParams params = BeanUtil.getProperty(rule, source.getUserData().toString());
    if (params == null) {
      params = new RequestParams();
      BeanUtil.setProperty(rule, source.getUserData().toString(), params);
    }
    ModalBox.none().title("??????????????????").cancel("??????").body(new ParamsEditor(params)).show();
  }

  @FXML
  public void debugRule() {
    if (debugPanel == null) {
      debugPanel = createDebugBox("?????????????????????", RuleTester::test);
    }
    showDebugBox(debugPanel, "????????????");
  }

  private void showDebugBox(Region panel, String title) {
    ModalBox modalBox = ModalBox.none();
    panel.setMinWidth(App.stage().getWidth() - 200);
    TextArea console = (TextArea) panel.lookup("#console");
    console.setMinHeight(App.stage().getHeight() / 2);
    modalBox.body(panel).cancel("??????").title(title).show();
  }

  /**
   * ??????????????????
   */
  @FXML
  public void debugSearchRule() {
    if (debugSearchPanel == null) {
      debugSearchPanel = createDebugBox("?????????????????????", RuleTester::search);
    }
    showDebugBox(debugSearchPanel, "????????????????????????");
  }

  /**
   * ??????????????????
   */
  @FXML
  public void debugTocRule() {
    if (debugTocPanel == null) {
      debugTocPanel = createDebugBox("?????????????????????", RuleTester::toc);
    }
    showDebugBox(debugTocPanel, "??????????????????");
  }

  /**
   * ??????????????????
   */
  @FXML
  public void debugDetailRule() {
    if (debugDetailPanel == null) {
      debugDetailPanel = createDebugBox("?????????????????????", RuleTester::detail);
    }
    showDebugBox(debugDetailPanel, "??????????????????");
  }

  /**
   * ??????????????????
   */
  @FXML
  public void debugContentRule() {
    if (debugContentPanel == null) {
      debugContentPanel = createDebugBox("?????????????????????", RuleTester::content);
    }
    showDebugBox(debugContentPanel, "??????????????????");
  }

  /**
   * ??????????????????
   *
   * @param promptText ????????????
   * @param starter    ?????????
   * @return ??????
   */
  @SuppressWarnings("unchecked")
  private VBox createDebugBox(String promptText, BiConsumer<RuleTester, String> starter) {
    TextArea console = new TextArea();
    NodeHelper.addClass(console, "rule-debug-console");
    console.setId("console");
    console.setWrapText(false);
    VBox debugBox = new VBox();
    debugBox.setSpacing(10);
    InputBox inputBox = new InputBox();
    inputBox.setIcon(IconFont.START.name());
    inputBox.setPrompt(promptText);
    AtomicBoolean running = new AtomicBoolean(false);
    RuleTester tester = new RuleTester(rule);
    inputBox.setOnCommit(e -> {
      if (running.get()) {
        boolean retry = (boolean) ModalBox.confirm(s -> {
        }).title("???????????????????????????????????????").submit("??????").showAndWait().orElse(false);
        if (retry) {
          return;
        }
        FluentTask<Object> task = (FluentTask<Object>) debugBox.getUserData();
        if (task != null) {
          task.cancel();
        }
      }
      running.set(true);
      console.clear();
      FluentTask<Object> task = TaskFactory.create(false, () -> {
        try {
          tester.init(rule.copy(), msg -> Executor.runFx(() -> console.appendText(msg)));
          starter.accept(tester, e.getInput());
        } catch (Exception exception) {
          Executor.runFx(() -> console.appendText(ExceptionUtil.getSimpleMessage(exception)));
          log.warn("????????????????????????", exception);
          exception.printStackTrace();
        } finally {
          running.set(false);
        }
        return null;
      }).onFinally(() -> debugBox.setUserData(null));
      debugBox.setUserData(task);
      task.start();
    });
    JFXCheckBox showSource = new JFXCheckBox("????????????");
    showSource.setSelected(tester.isShowSource());
    JFXCheckBox showRule = new JFXCheckBox("????????????");
    showRule.setSelected(tester.isShowRule());
    JFXCheckBox showAllData = new JFXCheckBox("????????????????????????");
    showAllData.setSelected(tester.isShowAllData());
    JFXCheckBox consoleWrap = new JFXCheckBox("????????????");
    showSource.selectedProperty().addListener(e -> tester.setShowSource(showSource.isSelected()));
    showRule.selectedProperty().addListener(e -> tester.setShowRule(showRule.isSelected()));
    showAllData.selectedProperty().addListener(e -> tester.setShowAllData(showAllData.isSelected()));
    consoleWrap.selectedProperty().bindBidirectional(console.wrapTextProperty());
    HBox optionsBox = new HBox(showSource, showRule, showAllData, consoleWrap);
    optionsBox.setSpacing(10);
    debugBox.getChildren().setAll(inputBox, console, optionsBox);
    return debugBox;
  }

  /**
   * ??????JS??????????????????
   */
  public void showScriptDebugBox() {
    if (debugScriptPanel == null) {
      debugScriptPanel = new ScriptDebugBox();
    }
    ModalBox.none().body(debugScriptPanel).title("???????????????????????????").show();
  }


  /**
   * ???????????????????????????????????????????????????????????????????????????????????????
   */
  @FXML
  private void goBack() {
    if (Objects.equals(rule, realRule)) {
      back(false);
    } else {
      ModalBox.confirm(confirm -> {
        if (Boolean.TRUE.equals(confirm)) {
          back(false);
        }
      }).message("???????????????????????????").show();
    }
  }

  /**
   * ???????????????????????????????????????????????????????????????????????????????????????
   */
  private void back(boolean save) {
    SidebarNavigateBundle bundle = new SidebarNavigateBundle();
    // ???????????????
    if (realRule == null && save) {
      bundle.put(BUNDLE_RULE_KEY, rule.copy());
    }
    navigation.navigate(from, bundle);
    this.realRule = null;
    this.rule = null;
  }

  /**
   * ????????????
   */
  @FXML
  private void save() {
    saveSource();
    if (rule.isEffective()) {
      if (realRule != null) {
        BeanUtil.copyProperties(rule, realRule, CopyOptions.create().ignoreNullValue());
      }
      // ?????????????????????????????????????????????????????????
      if (!fromManager && saveToRulesSwitch != null && saveToRulesSwitch.isSelected()) {
        RuleManager.addRule(realRule);
      }
      back(true);
    } else {
      Toast.error("???????????????????????????");
    }
  }

  /**
   * ??????????????????
   */
  @FXML
  private void showSource() {
    if (sourceEditor == null) {
      sourceEditor = new TextArea();
    }
    if (isSourceMode()) {
      if (saveSource()) {
        sourceEditor.setText(null);
        showSourceButton.setText("??????");
        panel.setContent(ruleContainer);
      }
    } else {
      showSourceButton.setText("??????");
      sourceEditor.setText(GsonUtils.PRETTY.toJson(rule));
      panel.setContent(sourceEditor);
    }
  }

  /**
   * ????????????
   *
   * @return true ??????
   */
  private boolean saveSource() {
    if (isSourceMode()) {
      reset();
      if (StringUtils.isNotBlank(sourceEditor.getText())) {
        try {
          rule = RuleHelper.parseRule(sourceEditor.getText(), AnalyzerRule.class);
          bindData();
        } catch (Exception e) {
          Toast.error("??????????????????");
          return false;
        }
      }
    }
    return true;
  }

  /**
   * ?????????????????????
   *
   * @return true ????????????
   */
  private boolean isSourceMode() {
    return panel.getContent() == sourceEditor;
  }


  /**
   * ???????????????Bean?????????????????????
   */
  private void bindData() {
    StringConverter<Integer> integerStringConverter = new StringConverter<>() {
      @Override
      public String toString(Integer object) {
        return object == null ? null : String.valueOf(object);
      }

      @Override
      public Integer fromString(String string) {
        return NumberUtil.isNumber(string) ? Integer.parseInt(string) : 0;
      }
    };
    StringConverter<Long> longStringConverter = new StringConverter<>() {
      @Override
      public String toString(Long object) {
        return object == null ? null : String.valueOf(object);
      }

      @Override
      public Long fromString(String string) {
        return NumberUtil.isNumber(string) ? Long.parseLong(string) : 0;
      }
    };
    bind(weight.focusedProperty(), weight::getText, weight::setText, rule::getWeight, rule::setWeight, integerStringConverter);
    bind(delayTime.focusedProperty(), delayTime::getText, delayTime::setText, rule.getContent()::getDelayTime, rule.getContent()::setDelayTime, longStringConverter);
    // ??????????????????
    inputBoxes.forEach(this::bind);
    inputs.forEach(this::bind);
    checkBoxes.forEach(this::bind);
    comboBoxes.forEach(this::bind);
  }

  /**
   * ?????? CheckBox ?????????????????????
   *
   * @param checkBox ?????????
   */
  private void bind(CheckBox checkBox) {
    String expression = checkBox.getUserData().toString();
    BooleanProperty property = checkBox.selectedProperty();
    property.set(Boolean.TRUE.equals(BeanUtil.getProperty(rule, expression)));
    InvalidationListener listener = e -> BeanUtil.setProperty(rule, expression, property.getValue());
    property.addListener(listener);
    listeners.put(property, listener);
  }

  /**
   * ?????? InputBox???TextField, ????????????????????????????????????
   *
   * @param field InputBox
   */
  private void bind(InputBox field) {
    String fieldExpression = field.getUserData().toString();
    BeanPath resolver = new BeanPath(fieldExpression);
    CommonRule ruleItem = (CommonRule) resolver.get(rule);
    if (ruleItem == null) {
      ruleItem = new CommonRule();
      resolver.set(rule, ruleItem);
    }
    // ????????????JSON
    CommonRule finalRuleItem = ruleItem;
    field.setOnCommit(event -> {
      if (editor == null) {
        editor = new CommonRuleEditor();
      }
      editor.setRule(finalRuleItem);
      // ????????????common rule ???page??????
      boolean showPage = StringUtils.startWith(fieldExpression, "search") && !CharSequenceUtil.equalsAny(fieldExpression, "search.detailPage", "search.list");
      editor.setShowPage(showPage);
      AtomicBoolean success = new AtomicBoolean(false);
      ModalBox.confirm(save -> {
        success.set(true);
        if (Boolean.TRUE.equals(save)) {
          CommonRule commonRule;
          try {
            commonRule = editor.getRule();
            BeanUtil.copyProperties(commonRule, finalRuleItem);
            field.getInput().setText(finalRuleItem.ruleString());
          } catch (JsonSyntaxException e) {
            Toast.error((StackPane) editor.getParent(), "???????????????");
            log.warn("??????JSON????????????????????????{} ???JSON???\n{}", field.getUserData(), editor.getJson(), e);
            success.set(false);
          } catch (Exception e) {
            Toast.error((StackPane) editor.getParent(), "???????????????");
            log.error("??????????????????????????????{} ???JSON???\n{}", field.getUserData(), editor.getJson(), e);
            success.set(false);
          }
        }
      }).body(editor).title("????????????").success(success::get).show();
    });
    bind(field.getInput(), CommonRule.ruleStringGetter(ruleItem), CommonRule.ruleStringSetter(ruleItem));
  }

  /**
   * ?????? TextInputControl, ????????????????????????????????????
   *
   * @param field TextInputControl
   */
  private void bind(TextInputControl field) {
    String expression = field.getUserData().toString();
    bind(field.focusedProperty(), field::getText, field::setText, () -> BeanUtil.getProperty(rule, expression), value -> BeanUtil.setProperty(rule, expression, value), null);
  }

  /**
   * ????????????
   *
   * @param field ComboBox
   */
  private void bind(ComboBox<String> field) {
    String expression = field.getUserData().toString();
    bind(field.focusedProperty(), field::getValue, field::setValue, () -> BeanUtil.getProperty(rule, expression), value -> BeanUtil.setProperty(rule, expression, value), null);
  }

  /**
   * ????????????
   *
   * @param field  TextInputControl
   * @param getter ???????????????JavaBean???getter
   * @param setter ???????????????JavaBean???setter
   * @param <T>    JavaBean??????
   */
  private <T> void bind(TextInputControl field, Supplier<T> getter, Consumer<T> setter) {
    bind(field.focusedProperty(), field::getText, field::setText, getter, setter, null);
  }

  /**
   * ????????????
   *
   * @param property       ??????????????????
   * @param propertyGetter ?????????getter
   * @param propertySetter ?????????setter
   * @param getter         ???????????????JavaBean???getter
   * @param setter         ???????????????JavaBean???setter
   * @param converter      ??????????????????
   * @param <T>            JavaBean??????
   */
  @SuppressWarnings("unchecked")
  private <T> void bind(ReadOnlyBooleanProperty property, Supplier<String> propertyGetter, Consumer<String> propertySetter, Supplier<T> getter, Consumer<T> setter, StringConverter<T> converter) {
    // ??????
    T initValue = getter.get();
    String initStrValue = converter == null ? (String) initValue : converter.toString(initValue);
    propertySetter.accept(initStrValue);
    // ????????????
    InvalidationListener listener = e -> {
      if (Boolean.FALSE.equals(property.get()) && !Objects.equals(propertyGetter.get(), getter.get())) {
        T value;
        if (converter == null) {
          value = (T) propertyGetter.get();
        } else {
          value = converter.fromString(propertyGetter.get());
        }
        setter.accept(value);
      }
    };
    property.addListener(listener);
    listeners.put(property, listener);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    RuleEditorView that = (RuleEditorView) o;
    return fromManager == that.fromManager && Objects.equals(listeners, that.listeners) && Objects
      .equals(inputBoxes, that.inputBoxes) && Objects.equals(inputs, that.inputs) && Objects.equals(checkBoxes, that.checkBoxes) && Objects
      .equals(comboBoxes, that.comboBoxes) && Objects.equals(cookieField, that.cookieField) && Objects.equals(contentRule, that.contentRule) && Objects
      .equals(autoAnalysisMode, that.autoAnalysisMode) && Objects.equals(infoItemsPanel, that.infoItemsPanel) && Objects.equals(sourceEditor, that.sourceEditor) && Objects
      .equals(panel, that.panel) && Objects.equals(showSourceButton, that.showSourceButton) && Objects.equals(ruleContainer, that.ruleContainer) && Objects
      .equals(weight, that.weight) && Objects.equals(editor, that.editor) && Objects.equals(debugContentPanel, that.debugContentPanel) && Objects
      .equals(debugTocPanel, that.debugTocPanel) && Objects.equals(debugDetailPanel, that.debugDetailPanel) && Objects.equals(debugSearchPanel, that.debugSearchPanel)
      && Objects.equals(debugPanel, that.debugPanel) && Objects.equals(rule, that.rule) && Objects.equals(realRule, that.realRule) && Objects
      .equals(from, that.from) && Objects.equals(saveToRule, that.saveToRule) && Objects.equals(saveToRulesSwitch, that.saveToRulesSwitch);
  }

  @Override
  public int hashCode() {
    return Objects
      .hash(super.hashCode(), listeners, inputBoxes, inputs, checkBoxes, comboBoxes, cookieField, contentRule, autoAnalysisMode, infoItemsPanel, sourceEditor, panel, showSourceButton,
        ruleContainer, weight, editor, debugContentPanel, debugTocPanel, debugDetailPanel, debugSearchPanel, debugPanel, rule, realRule, fromManager, from, saveToRule, saveToRulesSwitch);
  }
}
