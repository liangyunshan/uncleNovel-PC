package com.unclezs.novel.app.main.views.home;

import cn.hutool.core.io.FileUtil;
import com.unclezs.novel.analyzer.model.Novel;
import com.unclezs.novel.analyzer.spider.Spider;
import com.unclezs.novel.app.framework.annotation.FxView;
import com.unclezs.novel.app.framework.components.PlaceHolder;
import com.unclezs.novel.app.framework.components.TabButton;
import com.unclezs.novel.app.framework.components.Toast;
import com.unclezs.novel.app.framework.components.icon.Icon;
import com.unclezs.novel.app.framework.components.icon.IconFont;
import com.unclezs.novel.app.framework.components.sidebar.SidebarNavigateBundle;
import com.unclezs.novel.app.framework.components.sidebar.SidebarView;
import com.unclezs.novel.app.framework.serialize.PropertyJsonSerializer;
import com.unclezs.novel.app.framework.util.NodeHelper;
import com.unclezs.novel.app.main.core.spider.SpiderWrapper;
import com.unclezs.novel.app.main.db.beans.DownloadHistory;
import com.unclezs.novel.app.main.db.dao.DownloadHistoryDao;
import com.unclezs.novel.app.main.manager.ResourceManager;
import com.unclezs.novel.app.main.manager.SettingManager;
import com.unclezs.novel.app.main.model.BookBundle;
import com.unclezs.novel.app.main.model.config.DownloadConfig;
import com.unclezs.novel.app.main.util.MixPanelHelper;
import com.unclezs.novel.app.main.views.components.cell.DownloadActionTableCell;
import com.unclezs.novel.app.main.views.components.cell.DownloadHistoryActionTableCell;
import com.unclezs.novel.app.main.views.components.cell.ProgressBarTableCell;
import com.unclezs.novel.app.main.views.components.cell.TagsTableCell;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author blog.unclezs.com
 * @date 2021/4/25 9:40
 */
@Slf4j
@FxView(fxml = "/layout/home/download-manager.fxml")
@EqualsAndHashCode(callSuper = true)
public class DownloadManagerView extends SidebarView<StackPane> {

  /**
   * ??????????????????
   */
  public static final String BUNDLE_DOWNLOAD_KEY = "bundle_download_key";
  public static final File TMP_DIR = ResourceManager.cacheFile("downloads");
  private static final String PAGE_NAME = "????????????";
  /**
   * ????????????DAO
   */
  private final DownloadHistoryDao historyDao = new DownloadHistoryDao();
  @FXML
  private TabButton tasksTab;
  @FXML
  private TabButton historyTab;
  @FXML
  private VBox tasksPanel;
  private VBox historyPanel;
  @FXML
  private TableView<SpiderWrapper> tasksTable;
  private TableView<DownloadHistory> historyTable;
  @FXML
  private StackPane container;

  @Override
  public void onCreated() {
    createTasksTableColumns();
    restore();
    // ????????????????????????
    tasksTable.getItems().addListener((Observable observable) -> runTask());
    SettingManager.manager().getDownload().getTaskNum().addListener(e -> runTask());
    historyTab.setOnAction(e -> container.getChildren().setAll(getHistoryPanel()));
    tasksTab.setOnAction(e -> container.getChildren().setAll(tasksPanel));
  }

  @Override
  public void onShow(SidebarNavigateBundle bundle) {
    MixPanelHelper.event(PAGE_NAME);
    BookBundle bookBundle = bundle.get(BUNDLE_DOWNLOAD_KEY);
    if (bookBundle != null) {
      tasksTab.fireEvent(new ActionEvent());
      createTask(bookBundle);
    }
  }

  @Override
  public void onDestroy() {
    if (tasksTable.getItems().isEmpty()) {
      FileUtil.del(TMP_DIR);
    }
  }

  /**
   * ????????????????????????
   */
  private Node getHistoryPanel() {
    if (historyPanel == null) {
      historyPanel = new VBox(getHistoryTable());
    }
    return historyPanel;
  }

  /**
   * ?????????????????????????????????????????????
   *
   * @return ??????????????????
   */
  @SuppressWarnings("unchecked")
  private TableView<DownloadHistory> getHistoryTable() {
    if (historyTable == null) {
      historyTable = new TableView<>();
      historyTable.setPlaceholder(new PlaceHolder(localized("download.manager.history.placeholder")));
      VBox.setVgrow(historyTable, Priority.ALWAYS);
      historyTable.getItems().setAll(historyDao.selectAll());
      historyTable.getItems().addListener((ListChangeListener<DownloadHistory>) c -> {
        while (c.next()) {
          c.getRemoved().forEach(historyDao::delete);
          c.getAddedSubList().forEach(historyDao::save);
        }
      });
      // ??????
      TableColumn<DownloadHistory, Integer> id = NodeHelper.addClass(new TableColumn<>("#"), "id");
      id.prefWidthProperty().bind(historyTable.widthProperty().multiply(0.1));
      id.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(historyTable.getItems().indexOf(param.getValue()) + 1));
      // ??????
      TableColumn<DownloadHistory, String> name = new TableColumn<>(localized("download.manager.history.name"));
      name.prefWidthProperty().bind(historyTable.widthProperty().multiply(0.3));
      name.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getName()));
      // ??????
      TableColumn<DownloadHistory, String> type = new TableColumn<>(localized("download.manager.history.type"));
      type.prefWidthProperty().bind(historyTable.widthProperty().multiply(0.25));
      type.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getType()));
      type.setCellFactory(param -> new TagsTableCell<>());

      TableColumn<DownloadHistory, String> date = new TableColumn<>(localized("download.manager.history.date"));
      date.prefWidthProperty().bind(historyTable.widthProperty().multiply(0.2));
      date.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getDate()));
      // ??????
      TableColumn<DownloadHistory, DownloadHistory> operation = NodeHelper.addClass(new TableColumn<>(localized("download.manager.history.operation")), "align-center");
      operation.prefWidthProperty().bind(historyTable.widthProperty().multiply(0.1));
      operation.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue()));
      operation.setCellFactory(param -> new DownloadHistoryActionTableCell());

      historyTable.getColumns().addAll(id, name, type, date, operation);
      historyTable.getColumns().forEach(column -> column.setResizable(false));
      ContextMenu contextMenu = new ContextMenu();
      MenuItem clearHistory = new MenuItem("??????????????????", new Icon(IconFont.DELETE));
      clearHistory.setOnAction(e ->  historyTable.getItems().clear());
      contextMenu.getItems().add(clearHistory);
      historyTable.setContextMenu(contextMenu);
    }
    return historyTable;
  }

  /**
   * ??????????????????????????????
   */
  @SuppressWarnings("unchecked")
  private void createTasksTableColumns() {
    // ??????
    TableColumn<SpiderWrapper, Integer> id = NodeHelper.addClass(new TableColumn<>("#"), "id");
    id.prefWidthProperty().bind(tasksTable.widthProperty().multiply(0.1));
    id.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(tasksTable.getItems().indexOf(param.getValue()) + 1));
    // ??????
    TableColumn<SpiderWrapper, String> name = new TableColumn<>(localized("download.manager.running.name"));
    name.prefWidthProperty().bind(tasksTable.widthProperty().multiply(0.35));
    name.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getName()));
    // ??????
    TableColumn<SpiderWrapper, SpiderWrapper> progress = new TableColumn<>(localized("download.manager.running.progress"));
    progress.prefWidthProperty().bind(tasksTable.widthProperty().multiply(0.35));
    progress.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
    progress.setCellFactory(param -> new ProgressBarTableCell());
    // ??????
    TableColumn<SpiderWrapper, SpiderWrapper> operation = NodeHelper.addClass(new TableColumn<>(localized("download.manager.running.operation")), "download-action-col");
    operation.prefWidthProperty().bind(tasksTable.widthProperty().multiply(0.15));
    operation.setCellValueFactory(col -> new ReadOnlyObjectWrapper<>(col.getValue()));
    operation.setCellFactory(param -> new DownloadActionTableCell());

    tasksTable.getColumns().addAll(id, name, progress, operation);
    tasksTable.getColumns().forEach(column -> column.setResizable(false));
  }

  /**
   * ???????????????
   */
  public void onCompleted(SpiderWrapper wrapper) {
    // ????????????
    FileUtil.del(FileUtil.file(DownloadManagerView.TMP_DIR, wrapper.getId()));
    // ????????????
    tasksTable.getItems().remove(wrapper);
    // ???????????????????????????????????????????????????????????????
    DownloadHistory downloadHistory = DownloadHistory.fromWrapper(wrapper);
    if (historyTable != null) {
      historyTable.getItems().add(downloadHistory);
    } else {
      historyDao.save(downloadHistory);
    }
  }

  /**
   * ??????????????????????????????????????????
   */
  public void runTask() {
    // ??????????????????
    int canRunTasksNumber = canRunTasksNumber();
    if (canRunTasksNumber > 0) {
      // ??????????????????
      List<SpiderWrapper> waitingTask = tasksTable.getItems().stream()
        .filter(task -> task.isState(SpiderWrapper.WAIT_RUN))
        .collect(Collectors.toList());
      for (int i = 0; i < canRunTasksNumber && i < waitingTask.size(); i++) {
        waitingTask.get(i).run();
      }
      // ?????????????????????????????????????????????????????????
    } else if (canRunTasksNumber < 0) {
      List<SpiderWrapper> runningTask = tasksTable.getItems().stream()
        .filter(task -> task.getSpider().isState(Spider.RUNNING))
        .collect(Collectors.toList());
      for (int i = canRunTasksNumber; i < 0 && runningTask.size() + i >= 0; i++) {
        runningTask.get(runningTask.size() + i).waiting();
      }
    }
  }

  /**
   * ??????????????????
   *
   * @param bundle ???????????????
   */
  private void createTask(BookBundle bundle) {
    DownloadConfig downloadConfig = SettingManager.manager().getDownload();
    String savePath = downloadConfig.getFolder().getValue();
    if (!FileUtil.mkdir(savePath).exists()) {
      Toast.error("????????????????????????????????????????????????");
      return;
    }
    Novel novel = bundle.getNovel();
    Spider spider = new Spider();
    spider.setUrl(novel.getUrl());
    spider.setNovel(bundle.getNovel());
    spider.setAnalyzerRule(bundle.getRule());
    spider.setRetryTimes(downloadConfig.getRetryNum().getValue());
    spider.setSavePath(savePath);
    spider.setThreadNum(downloadConfig.getThreadNum().getValue());
    SpiderWrapper spiderWrapper = new SpiderWrapper(spider, this::onCompleted);
    tasksTable.getItems().add(spiderWrapper);
    // ?????????????????????????????????????????????
    if (canRunTasksNumber() > 0) {
      spiderWrapper.run();
    }
  }

  /**
   * ?????????????????????????????????
   *
   * @return ????????????
   */
  public int canRunTasksNumber() {
    Integer maxTaskNum = SettingManager.manager().getDownload().getTaskNum().get();
    long currentRunning = tasksTable.getItems().stream().filter(task -> task.isState(Spider.RUNNING)).count();
    return (int) (maxTaskNum - currentRunning);
  }


  /**
   * ????????????????????????????????????
   */
  public void restore() {
    if (FileUtil.exist(TMP_DIR)) {
      List<String> names = FileUtil.listFileNames(TMP_DIR.getAbsolutePath());
      List<SpiderWrapper> tasks = new ArrayList<>();
      for (String name : names) {
        File file = FileUtil.file(TMP_DIR, name);
        try {
          String json = FileUtil.readUtf8String(file);
          SpiderWrapper task = PropertyJsonSerializer.fromJson(json, SpiderWrapper.class);
          task.setId(name);
          task.init(this::onCompleted);
          task.pause();
          tasks.add(task);
        } catch (Exception e) {
          log.warn("?????????????????????????????????{}", file.getAbsoluteFile(), e);
          FileUtil.del(file);
        }
      }
      tasksTable.getItems().setAll(tasks);
    }
  }
}
