package com.unclezs.novel.app.main.views.home;

import cn.hutool.core.io.FileUtil;
import com.jfoenix.controls.JFXMasonryPane;
import com.jfoenix.controls.JFXNodesList;
import com.jfoenix.controls.JFXTabPane;
import com.unclezs.novel.analyzer.core.comparator.ChapterComparator;
import com.unclezs.novel.analyzer.model.Chapter;
import com.unclezs.novel.analyzer.spider.NovelSpider;
import com.unclezs.novel.analyzer.spider.helper.SpiderHelper;
import com.unclezs.novel.analyzer.util.StringUtils;
import com.unclezs.novel.app.framework.annotation.FxView;
import com.unclezs.novel.app.framework.appication.SceneNavigateBundle;
import com.unclezs.novel.app.framework.components.ModalBox;
import com.unclezs.novel.app.framework.components.Toast;
import com.unclezs.novel.app.framework.components.sidebar.SidebarNavigateBundle;
import com.unclezs.novel.app.framework.components.sidebar.SidebarView;
import com.unclezs.novel.app.framework.core.AppContext;
import com.unclezs.novel.app.framework.executor.Executor;
import com.unclezs.novel.app.framework.executor.TaskFactory;
import com.unclezs.novel.app.framework.util.Choosers;
import com.unclezs.novel.app.framework.util.EventUtils;
import com.unclezs.novel.app.main.App;
import com.unclezs.novel.app.main.db.beans.Book;
import com.unclezs.novel.app.main.db.dao.BookDao;
import com.unclezs.novel.app.main.manager.ResourceManager;
import com.unclezs.novel.app.main.manager.SettingManager;
import com.unclezs.novel.app.main.model.BookBundle;
import com.unclezs.novel.app.main.model.BookCache;
import com.unclezs.novel.app.main.model.config.BookShelfConfig;
import com.unclezs.novel.app.main.util.BookHelper;
import com.unclezs.novel.app.main.util.MixPanelHelper;
import com.unclezs.novel.app.main.views.components.BookNode;
import com.unclezs.novel.app.main.views.components.cell.TocListCell;
import com.unclezs.novel.app.main.views.reader.ReaderView;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小说书架
 *
 * @author blog.unclezs.com
 * @date 2021/4/25 9:40
 */
@Slf4j
@FxView(fxml = "/layout/home/fiction-bookshelf.fxml")
@EqualsAndHashCode(callSuper = true)
public class FictionBookshelfView extends SidebarView<StackPane> {

  public static final String BUNDLE_BOOK_KEY = "bundle-book-key";
  public static final String GROUP_ALL = "全部";
  public static final String GROUP_LOCAL = "本地";
  public static final String CACHE_FOLDER_NAME = "book";
  public static final File CACHE_FOLDER = ResourceManager.cacheFile(CACHE_FOLDER_NAME);
  private static final String PAGE_NAME = "小说书架";
  private final ObservableList<BookNode> bookNodes = FXCollections.observableArrayList();
  private final BookDao bookDao = new BookDao();
  private final ObservableSet<String> groups = FXCollections.observableSet();
  @FXML
  private JFXNodesList floatButtons;
  @FXML
  private JFXTabPane groupPanel;
  @FXML
  private JFXMasonryPane bookPanel;
  @FXML
  private ContextMenu bookNodeContextMenu;
  @FXML
  private ContextMenu groupTabContextMenu;
  /**
   * 书架配置
   */
  private BookShelfConfig bookShelfConfig;

  @Override
  public void onCreated() {
    this.bookShelfConfig = SettingManager.manager().getBookShelf();
    // 分组初始化
    String selectedGroup = bookShelfConfig.getGroup();
    groups.addListener((SetChangeListener<String>) change -> {
      if (change.wasAdded()) {
        addGroup(change.getElementAdded());
      }
      if (change.wasRemoved()) {
        deleteGroup(change.getElementRemoved());
      }
    });
    groups.add(GROUP_ALL);
    // 查库获取书籍列表，并且初始化分组信息
    bookDao.selectAll().forEach(book -> {
      this.addBook(book);
      if (book.getGroup() != null) {
        this.groups.add(book.getGroup());
      }
    });
    // 书籍节点变化监听
    bookNodes.addListener((ListChangeListener<BookNode>) c -> {
      while (c.next()) {
        c.getRemoved().forEach(bookNode -> {
          bookPanel.getChildren().remove(bookNode);
          bookDao.delete(bookNode.getBook());
        });
        c.getAddedSubList().forEach(bookNode -> {
          bookDao.save(bookNode.getBook());
          String currentGroup = groupPanel.getTabs().stream().filter(Tab::isSelected).map(Tab::getText).findFirst().orElse(null);
          if (bookNode.getBook().getGroup() != null) {
            groups.add(bookNode.getBook().getGroup());
          }
          if (Objects.equals(currentGroup, GROUP_ALL) || Objects.equals(currentGroup, bookNode.getBook().getGroup())) {
            bookPanel.getChildren().add(bookNode);
          }
        });
      }
      bookPanel.requestLayout();
    });
    // 初始化选中的分组
    if (GROUP_ALL.equals(selectedGroup)) {
      bookPanel.getChildren().setAll(bookNodes);
    } else {
      groupPanel.getTabs().stream()
        .filter(tab -> tab.getText().equals(selectedGroup))
        .findFirst().ifPresent(tab -> groupPanel.getSelectionModel().select(tab));
    }
    // 自动检测更新
    if (Boolean.TRUE.equals(this.bookShelfConfig.getAutoUpdate().get())) {
      checkUpdateGroup();
    }
    // 标题显示监听
    listenerTitleVisible();
  }

  @Override
  public void onShow(SidebarNavigateBundle bundle) {
    MixPanelHelper.event(PAGE_NAME);
    BookBundle bookBundle = bundle.get(BUNDLE_BOOK_KEY);
    if (bookBundle != null) {
      Book book = Book.fromBookBundle(bookBundle);
      // 封面
      BookHelper.downloadCover(book.getCover(), book.getUrl(), FileUtil.file(CACHE_FOLDER, book.getId()), cover -> {
        book.setCover(cover);
        bookDao.update(book);
      });
      // 缓存章节
      cacheBook(book);
      addBook(book);
    }
  }

  /**
   * 导入本地小说
   */
  @FXML
  public void importBook() {
    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("文本文件", "*.txt");
    fileChooser.getExtensionFilters().addAll(filter);
    File file = fileChooser.showOpenDialog(App.stage());
    if (file == null) {
      return;
    }
    SidebarNavigateBundle bundle = new SidebarNavigateBundle();
    bundle.put(ImportBookView.BUNDLE_FILE_KEY, file);
    navigation.navigate(ImportBookView.class, bundle);
  }

  /**
   * 添加一本书到书架
   *
   * @param book 书籍
   */
  public void addBook(Book book) {
    BookNode bookNode = new BookNode(book);
    bookNode.setOnContextMenuRequested(e -> {
      bookNodeContextMenu.show(bookNode, e.getScreenX(), e.getScreenY());
      e.consume();
    });
    EventUtils.setOnMousePrimaryClick(bookNode, e -> readBook(bookNode));
    bookNodes.add(bookNode);
  }

  /**
   * 添加或更新书架的一本书
   *
   * @param book 书籍
   */
  public void addOrUpdateBook(Book book) {
    bookNodes.stream().filter(bookNode -> Objects.equals(bookNode.getBook().getId(), book.getId())).findFirst().ifPresent(bookNodes::remove);
    addBook(book);
  }

  /**
   * 打开阅读器阅读
   *
   * @param bookNode 书籍节点
   */
  public void readBook(BookNode bookNode) {
    Book book = bookNode.getBook();
    if (book.isLocal() && !FileUtil.exist(book.getUrl())) {
      Toast.error("文件不存在");
      return;
    }
    // 更新状态
    if (book.isUpdate()) {
      bookNode.setUpdate(false);
      bookDao.update(book);
    }
    SceneNavigateBundle bundle = new SceneNavigateBundle();
    bundle.put(ReaderView.BUNDLE_READ_BOOK_KEY, book);
    AppContext.getView(HomeView.class).getApp().navigate(ReaderView.class, bundle);
  }

  /**
   * 上下文菜单 开始阅读
   */
  @FXML
  private void readBook() {
    readBook((BookNode) bookNodeContextMenu.getOwnerNode());
  }

  /**
   * 上下文菜单 下载本书
   */
  @FXML
  private void addDownload() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    Book book = node.getBook();
    if (book.isLocal()) {
      Toast.error("本地小说无需下载");
      return;
    }
    BookCache cache = BookHelper.loadCache(FileUtil.file(CACHE_FOLDER, book.getId()));
    BookHelper.submitDownload(book.toNovel(), cache.getRule(), cache.getToc());
  }

  /**
   * 上下文菜单 解析下载
   */
  @FXML
  private void analysisDownload() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    Book book = node.getBook();
    if (book.isLocal()) {
      Toast.error("本地小说无需下载");
      return;
    }
    SidebarNavigateBundle bundle = new SidebarNavigateBundle().put(AnalysisView.BUNDLE_KEY_NOVEL_INFO, book.toNovel());
    AppContext.getView(AnalysisView.class).getNavigation().navigate(AnalysisView.class, bundle);
  }

  /**
   * 上下文菜单 重命名书籍
   */
  @FXML
  private void renameBook() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    String initName = node.getBook().getName();
    ModalBox.input(initName, "请输入新的小说名称", newName -> {
      node.setTitle(newName);
      bookDao.update(node.getBook());
      Toast.success("修改成功");
    }).title("重命名小说").show();
  }

  /**
   * 上下文菜单 重命名书籍
   */
  @FXML
  private void changeCover() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    File file = Choosers.chooseImage("小说封面");
    if (file != null) {
      // 复制封面到缓存目录
      Book book = node.getBook();
      File cover = FileUtil.copy(file, FileUtil.file(CACHE_FOLDER, book.getId(), BookHelper.COVER_NAME), true);
      node.setCover(cover.getAbsolutePath());
      bookDao.update(book);
    }
  }

  /**
   * 获取封面
   */
  @FXML
  public void searchCover() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    Book book = node.getBook();
    String name = book.getName();
    TaskFactory.create(() -> {
      String cover = SpiderHelper.getCover(name);
      log.trace("获取到《{}》小说封面：{}", cover, name);
      // 封面
      cover = BookHelper.downloadCover(cover, null, FileUtil.file(CACHE_FOLDER, book.getId()));
      return cover;
    }).onSuccess(cover -> {
      node.setCover(cover);
      bookDao.update(book);
      Toast.success("封面获取成功");
    }).onFailed(e -> {
      log.error("封面获取失败：{}", book.getName(), e);
      Toast.error("封面获取失败");
    }).start();
  }

  /**
   * 标题显示监听
   */
  private void listenerTitleVisible() {
    bookShelfConfig.getAlwaysShowBookTitle().addListener(e -> bookNodes.forEach(node -> node.showTitle(bookShelfConfig.getAlwaysShowBookTitle().get())));
  }

  /**
   * 上下文菜单 获取更新
   */
  @FXML
  private void checkUpdate() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    if (node.getBook().isLocal()) {
      Toast.warn("本地书籍无需更新");
      return;
    }
    TaskFactory.create(() -> checkUpdateGroup(node))
      .onSuccess(news -> {
        if (news.isEmpty()) {
          Toast.success("暂无更新");
        } else {
          ListView<Chapter> view = new ListView<>();
          view.setCellFactory(param -> new TocListCell());
          view.getItems().setAll(news);
          view.setMaxHeight(200);
          ModalBox.none().body(view).title("发现新章节").show();
        }
      }).onFailed(e -> {
      Toast.error("获取更新失败");
      log.error("获取书籍更新失败：{}", node.getBook().getName(), e);
    }).start();
  }


  /**
   * 获取当前分组的更新
   */
  @FXML
  private void checkUpdateGroup() {
    floatButtons.animateList(false);
    // todo 控制线程数量
    for (Node node : bookPanel.getChildren()) {
      if (node instanceof BookNode) {
        BookNode bookNode = (BookNode) node;
        // 只更新非本地书籍
        if (!bookNode.getBook().isLocal()) {
          bookNode.setUpdateTaskState(true);
          TaskFactory.create(false, () -> checkUpdateGroup(bookNode))
            .onFinally(() -> bookNode.setUpdateTaskState(false))
            .start();
        }
      }
    }
  }

  /**
   * 获取当前分组的更新
   *
   * @return 更新的章节列表
   */
  private List<Chapter> checkUpdateGroup(BookNode bookNode) throws IOException {
    Book book = bookNode.getBook();
    if (book.isLocal()) {
      return Collections.emptyList();
    }
    File cacheFile = FileUtil.file(CACHE_FOLDER, book.getId());
    BookCache cache = BookHelper.loadCache(cacheFile);
    NovelSpider novelSpider = new NovelSpider(cache.getRule());
    List<Chapter> toc = novelSpider.toc(book.getUrl());
    if (cache.getToc().size() == toc.size()) {
      return Collections.emptyList();
    }
    // 新的章节
    Set<String> olds = cache.getToc().stream().map(Chapter::getUrl).collect(Collectors.toSet());
    List<Chapter> newChapters = new ArrayList<>();
    for (Chapter chapter : toc) {
      if (!olds.contains(chapter.getUrl())) {
        newChapters.add(chapter);
      }
    }
    // 更新章节
    cache.getToc().addAll(newChapters);
    BookHelper.cache(cache, cacheFile);
    // 标记更新
    Executor.runFx(() -> bookNode.setUpdate(true));
    bookDao.update(book);
    // 返回更新的章节
    return newChapters;
  }

  /**
   * 缓存书籍
   *
   * @param book 书籍
   */
  private void cacheBook(Book book) {
    BookHelper.cache(new BookCache(book.getRule(), book.getToc()), FileUtil.file(CACHE_FOLDER, book.getId()));
  }


  /**
   * 删除小说
   */
  @FXML
  public void deleteBook() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    Book book = node.getBook();
    // 删除缓存文件
    FileUtil.del(FileUtil.file(CACHE_FOLDER, book.getId()));
    // 如果是本地小说则提示是否删除本地小说文件
    if (book.isLocal() && FileUtil.exist(book.getUrl())) {
      ModalBox.confirm(delete -> {
        if (Boolean.TRUE.equals(delete)) {
          FileUtil.del(book.getUrl());
        }
      }).message("是否删除本地小说文件").cancel("不了").submit("删除").showAndWait();
    }
    bookNodes.remove(node);
  }

  /**
   * 添加分组tab
   *
   * @param name 分组名字
   */
  private void addGroup(String name) {
    if (name == null) {
      return;
    }
    Tab tab = new Tab(name);
    // 切换分组
    tab.setOnSelectionChanged(e -> {
      if (tab.isSelected()) {
        if (GROUP_ALL.equals(name)) {
          bookPanel.getChildren().setAll(bookNodes);
        } else {
          List<BookNode> nodes = this.bookNodes.stream()
            .filter(bookNode -> name.equals(bookNode.getBook().getGroup()))
            .collect(Collectors.toList());
          bookPanel.getChildren().setAll(nodes);
        }
        tab.setContent(bookPanel);
        // 保存选中的分组，下次启动直接显示
        bookShelfConfig.setGroup(name);
      } else {
        tab.setContent(null);
      }
    });
    if (!GROUP_ALL.equals(name)) {
      // 上下文菜单
      tab.setContextMenu(groupTabContextMenu);
    } else {
      tab.setContent(bookPanel);
    }
    groupPanel.getTabs().add(tab);
  }

  /**
   * 设置分组
   */
  @FXML
  private void setGroup() {
    BookNode node = (BookNode) bookNodeContextMenu.getOwnerNode();
    Book book = node.getBook();
    ComboBox<String> groupSelector = new ComboBox<>();
    groupSelector.setMaxWidth(Double.MAX_VALUE);
    groupSelector.setEditable(true);
    groupSelector.setValue(book.getGroup());
    groupSelector.getItems().addAll(groups);
    ModalBox.confirm(save -> {
      if (Boolean.TRUE.equals(save)) {
        String group = groupSelector.getValue();
        if (StringUtils.isNotBlank(group)) {
          groups.add(group);
        } else {
          group = null;
        }
        // 改名后是否还属于当前的分组
        if (!Objects.equals(book.getName(), group) && book.getGroup() != null) {
          bookPanel.getChildren().remove(node);
        }
        node.getBook().setGroup(group);
        bookDao.update(book);
      }
    }).title("设置分组").body(groupSelector).show();
  }

  /**
   * 上下文菜单触发删除
   */
  @FXML
  private void deleteGroup() {
    String group = ((Label) groupTabContextMenu.getOwnerNode()).getText();
    groups.remove(group);
  }

  /**
   * 删除分组 tab、book的分组
   *
   * @param group 分组
   */
  private void deleteGroup(String group) {
    if (group == null) {
      return;
    }
    bookNodes.forEach(bookNode -> {
      if (group.equals(bookNode.getBook().getGroup())) {
        bookNode.getBook().setGroup(null);
      }
    });
    groupPanel.getTabs().removeIf(tab -> group.equals(tab.getText()));
  }
}
