package com.unclezs.novel.app.main.views.home;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jfoenix.controls.JFXDrawer;
import com.jfoenix.controls.JFXDrawersStack;
import com.jfoenix.controls.JFXProgressBar;
import com.jfoenix.controls.JFXSlider;
import com.unclezs.novel.analyzer.model.Chapter;
import com.unclezs.novel.analyzer.model.ChapterState;
import com.unclezs.novel.analyzer.request.Http;
import com.unclezs.novel.analyzer.request.RequestParams;
import com.unclezs.novel.analyzer.spider.NovelSpider;
import com.unclezs.novel.analyzer.util.StringUtils;
import com.unclezs.novel.analyzer.util.uri.UrlUtils;
import com.unclezs.novel.app.framework.annotation.FxView;
import com.unclezs.novel.app.framework.components.Toast;
import com.unclezs.novel.app.framework.components.icon.Icon;
import com.unclezs.novel.app.framework.components.icon.IconFont;
import com.unclezs.novel.app.framework.components.sidebar.SidebarNavigateBundle;
import com.unclezs.novel.app.framework.components.sidebar.SidebarView;
import com.unclezs.novel.app.framework.executor.Executor;
import com.unclezs.novel.app.framework.executor.FluentTask;
import com.unclezs.novel.app.framework.executor.TaskFactory;
import com.unclezs.novel.app.framework.util.DesktopUtils;
import com.unclezs.novel.app.framework.util.EventUtils;
import com.unclezs.novel.app.main.db.beans.AudioBook;
import com.unclezs.novel.app.main.db.dao.AudioBookDao;
import com.unclezs.novel.app.main.manager.ResourceManager;
import com.unclezs.novel.app.main.model.BookBundle;
import com.unclezs.novel.app.main.model.BookCache;
import com.unclezs.novel.app.main.util.BookHelper;
import com.unclezs.novel.app.main.util.MixPanelHelper;
import com.unclezs.novel.app.main.util.TimeUtil;
import com.unclezs.novel.app.main.views.components.cell.AudioBookListCell;
import com.unclezs.novel.app.main.views.components.cell.TocListCell;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @author blog.unclezs.com
 * @date 2021/4/25 9:40
 */
@Slf4j
@FxView(fxml = "/layout/home/audio-bookshelf.fxml")
@EqualsAndHashCode(callSuper = true)
public class AudioBookShelfView extends SidebarView<StackPane> {

  public static final String BUNDLE_BOOK_KEY = "bundle-audio-book";
  /**
   * ??????????????????
   */
  public static final String CACHE_FOLDER_NAME = "audio";
  public static final File CACHE_FOLDER = ResourceManager.cacheFile(CACHE_FOLDER_NAME);
  private static final String INIT_TIME = "00:00";
  private static final String PAGE_NAME = "????????????";
  private final AudioBookDao audioBookDao = new AudioBookDao();
  /**
   * ?????????
   */
  private ProgressChangeListener progressChangeListener;
  @FXML
  private JFXProgressBar loading;
  @FXML
  private Icon playButton;
  @FXML
  private Label currentTime;
  @FXML
  private Label totalTime;
  @FXML
  private JFXSlider progress;
  @FXML
  private ContextMenu bookContextMenu;
  @FXML
  private Label titleLabel;
  @FXML
  private Label chapterLabel;
  @FXML
  private ListView<Chapter> tocListView;
  @FXML
  private ListView<AudioBook> bookListView;
  @FXML
  private JFXDrawer tocDrawer;
  @FXML
  private JFXDrawersStack drawer;
  /**
   * ?????????????????????
   */
  private AudioBook currentBook;
  /**
   * ???????????????
   */
  private MediaPlayer player;
  /**
   * ??????????????????
   */
  private FluentTask<Chapter> loadMediaTask = null;

  @Override
  public void onCreated() {
    // ??????cell
    bookListView.setCellFactory(param -> new AudioBookListCell(bookContextMenu, book -> loadBook(book, true)));
    bookListView.getItems().addAll(audioBookDao.selectAll());
    bookListView.getItems().addListener((ListChangeListener<AudioBook>) c -> {
      while (c.next()) {
        c.getRemoved().forEach(book -> {
          if (book == currentBook) {
            releaseResource();
          }
          audioBookDao.delete(book);
          FileUtil.del(FileUtil.file(CACHE_FOLDER, book.getId()));
        });
        c.getAddedSubList().forEach(audioBookDao::save);
      }
    });
    // ????????????
    tocListView.setCellFactory(param -> new TocListCell());
    EventUtils.setOnMousePrimaryClick(tocListView, e -> {
      if (!tocListView.getSelectionModel().isEmpty()) {
        currentBook.setCurrentChapterIndex(tocListView.getSelectionModel().getSelectedIndex());
        drawer.toggle(tocDrawer);
        playChapter();
      }
    });
    // ??????????????????
    initProgress();
    // ??????????????????
    if (!bookListView.getItems().isEmpty()) {
      loadBook(bookListView.getItems().get(0), false);
      bookListView.getSelectionModel().selectFirst();
    }
  }

  @Override
  public void onShow(SidebarNavigateBundle bundle) {
    MixPanelHelper.event(PAGE_NAME);
    BookBundle bookBundle = bundle.get(BUNDLE_BOOK_KEY);
    if (bookBundle != null) {
      AudioBook book = AudioBook.fromBookBundle(bookBundle);
      // ??????
      int order = 1;
      for (Chapter chapter : book.getToc()) {
        chapter.setOrder(order++);
      }
      cacheBook(book);
      // ??????
      BookHelper.downloadCover(book.getCover(), book.getUrl(), FileUtil.file(CACHE_FOLDER, book.getId()), cover -> {
        book.setCover(cover);
        audioBookDao.update(book);
      });
      bookListView.getItems().add(book);
    }
  }

  @Override
  public void onDestroy() {
    if (currentBook != null) {
      // ????????????????????????
      cacheBook(currentBook);
      // ??????????????????
      audioBookDao.update(currentBook);
    }
  }

  /**
   * ?????????????????????????????????
   *
   * @param book ??????
   */
  public void addOrUpdateBook(AudioBook book) {
    bookListView.getItems().stream().filter(b -> Objects.equals(book.getId(), b.getId())).findFirst().ifPresent(bookListView.getItems()::remove);
    bookListView.getItems().add(book);
  }

  /**
   * ????????????
   *
   * @param book ??????
   */
  private void cacheBook(AudioBook book) {
    BookHelper.cache(new BookCache(book.getRule(), book.getToc()), FileUtil.file(CACHE_FOLDER, book.getId()));
  }

  /**
   * ????????????
   *
   * @param book ??????
   */
  private void loadCache(AudioBook book) {
    BookCache bookCache = BookHelper.loadCache(FileUtil.file(CACHE_FOLDER, book.getId()));
    book.setToc(bookCache.getToc());
    book.setRule(bookCache.getRule());
  }

  /**
   * ??????????????????
   */
  private void initProgress() {
    progress.setValueFactory(slider -> Bindings.createStringBinding(() -> {
      if (player == null) {
        return INIT_TIME;
      }
      double total = player.getTotalDuration().toSeconds();
      double current = total * slider.getValue();
      return TimeUtil.secondToTime(current) + StrUtil.SLASH + TimeUtil.secondToTime(total);
    }, progress.valueProperty()));
    progress.valueChangingProperty().addListener(e -> {
      if (!progress.isValueChanging() && player != null) {
        player.seek(player.getStopTime().multiply(progress.getValue()));
      }
    });
    progress.valueProperty().addListener(e -> {
      if (!progress.isValueChanging() && player != null) {
        Duration to = player.getStopTime().multiply(progress.getValue());
        if (Math.abs(player.getCurrentTime().subtract(to).toSeconds()) > 1) {
          player.seek(to);
        }
      }
    });
  }

  /**
   * ???????????????
   *
   * @param book ???????????????
   */
  private void loadBook(AudioBook book, boolean play) {
    if (book == null || currentBook == book) {
      return;
    }
    if (currentBook != null) {
      cacheBook(currentBook);
      audioBookDao.update(currentBook);
    }
    currentBook = book;
    // ?????????????????????????????????????????????
    if (CollUtil.isEmpty(book.getToc())) {
      loadCache(currentBook);
    }
    titleLabel.setText(book.getName());
    playChapter(currentBook.getCurrentProgress(), play);
  }

  /**
   * ????????????????????????
   */
  @FXML
  private void showToc() {
    if (currentBook == null) {
      return;
    }
    tocListView.getItems().setAll(currentBook.getToc());
    tocListView.getSelectionModel().select(currentBook.getCurrentChapterIndex());
    drawer.toggle(tocDrawer);
  }

  /**
   * ????????????
   */
  @FXML
  private void removeBook() {
    if (bookListView.getSelectionModel().isEmpty()) {
      return;
    }
    AudioBook book = bookListView.getSelectionModel().getSelectedItem();
    bookListView.getItems().remove(book);
    log.trace("???????????????????????????: {}", book);
  }


  /**
   * ??????????????????
   */
  @FXML
  private void playPrevious() {
    // ?????????
    if (player != null && currentBook != null && currentBook.getCurrentChapterIndex() > 0) {
      currentBook.setCurrentChapterIndex(currentBook.getCurrentChapterIndex() - 1);
      playChapter();
    }
  }

  /**
   * ??????????????????
   */
  @FXML
  private void playNext() {
    // ?????????
    if (player != null && currentBook != null && currentBook.getCurrentChapterIndex() < currentBook.getToc().size() - 1) {
      currentBook.setCurrentChapterIndex(currentBook.getCurrentChapterIndex() + 1);
      playChapter();
    }
  }


  /**
   * ????????????????????????????????????
   */
  private void playChapter() {
    playChapter(0, true);
  }

  /**
   * ??????????????????
   *
   * @param initProgress ????????????
   * @param play         ????????????
   */
  private void playChapter(double initProgress, boolean play) {
    if (currentBook.getToc().isEmpty()) {
      return;
    }
    Chapter chapter = currentBook.getToc().get(currentBook.getCurrentChapterIndex());
    loading.setVisible(true);
    // ??????????????????
    this.chapterLabel.setText(chapter.getName());
    this.currentBook.setCurrentChapterName(chapter.getName());
    // ??????????????????
    if (chapter.getContent() == null || UrlUtils.isHttpUrl(chapter.getContent())) {
      loadChapter(initProgress, play, chapter);
    } else {
      initPlayer(chapter, initProgress, play);
    }
    int next = currentBook.getCurrentChapterIndex() + 1;
    if (next < currentBook.getToc().size()) {
      loadChapter(currentBook, next);
    }
  }

  /**
   * ??????????????????
   *
   * @param initProgress ????????????
   * @param play         ????????????
   * @param chapter      ??????
   */
  private void loadChapter(double initProgress, boolean play, Chapter chapter) {
    // ??????????????????
    if (loadMediaTask != null) {
      loadMediaTask.cancel();
    }
    loadMediaTask = new FluentTask<Chapter>(false) {
      @Override
      protected Chapter call() {
        loadChapter(currentBook, chapter);
        return chapter;
      }
    }.onSuccess(c -> initPlayer(c, initProgress, play))
      .onFailed(e -> {
        // ???????????????????????????
        if (play) {
          Toast.error("??????????????????");
        }
        log.error("?????????????????????{}", chapter, e);
        loading.setVisible(false);
      });
    loadMediaTask.start();
  }


  /**
   * ????????????????????????????????????
   *
   * @param book  ??????
   * @param index ????????????
   */
  private void loadChapter(AudioBook book, int index) {
    Chapter chapter = book.getToc().get(index);
    if (chapter.getContent() != null && !UrlUtils.isHttpUrl(chapter.getContent())) {
      return;
    }
    Executor.run(() -> loadChapter(book, chapter));
  }

  /**
   * ???????????????
   *
   * @param book    ??????
   * @param chapter ??????
   */
  private void loadChapter(AudioBook book, Chapter chapter) {
    try {
      String mediaUrl = chapter.getContent();
      if (!UrlUtils.isHttpUrl(mediaUrl)) {
        mediaUrl = new NovelSpider(book.getRule()).content(chapter.getUrl());
        chapter.setContent(mediaUrl);
        chapter.setState(ChapterState.DOWNLOADED);
      }
      log.trace("??????????????????????????????{}", mediaUrl);
    } catch (Exception e) {
      log.warn("??????????????????????????????{}", chapter, e);
    }
  }


  /**
   * ??????/??????
   *
   * @param event ??????????????????
   */
  @FXML
  private void play(MouseEvent event) {
    Icon playBtn = (Icon) event.getSource();
    if (IconFont.PLAY.name().equalsIgnoreCase(playBtn.getValue().toString())) {
      play();
    } else {
      pause();
    }
  }

  /**
   * ??????
   */
  private void play() {
    if (player != null && currentBook != null) {
      playButton.setValue(IconFont.PLAY_PAUSE);
      player.play();
    }
  }

  /**
   * ??????
   */
  private void pause() {
    if (player != null && currentBook != null) {
      playButton.setValue(IconFont.PLAY);
      player.pause();
    }
  }

  /**
   * ??????????????????
   */
  private void releaseResource() {
    if (loadMediaTask != null) {
      loadMediaTask.cancel();
    }
    if (player != null) {
      pause();
      player.dispose();
    }
    currentBook = null;
  }


  /**
   * ??????????????????
   *
   * @param chapter      ??????
   * @param initProgress ????????????
   * @param play         ????????????
   */
  private void initPlayer(Chapter chapter, double initProgress, boolean play) {
    if (chapter.getState() != ChapterState.DOWNLOADED && StringUtils.isBlank(chapter.getUrl())) {
      return;
    }
    Media media = new Media(chapter.getUrl());
    if (player != null) {
      pause();
      player.dispose();
      // ????????????
      player.currentTimeProperty().removeListener(progressChangeListener);
    } else {
      // ???????????????
      progressChangeListener = new ProgressChangeListener();
    }
    player = new MediaPlayer(media);
    // ????????????
    player.currentTimeProperty().addListener(progressChangeListener);
    player.setOnError(() -> {
      if (play) {
        Toast.error("?????????????????????" + player.getError().getType());
      }
      loading.setVisible(false);
    });
    player.setOnReady(() -> {
      currentTime.setText(INIT_TIME);
      totalTime.setText(TimeUtil.secondToTime(player.getStopTime().toSeconds()));
      player.seek(player.getStopTime().multiply(initProgress));
      progress.setValue(initProgress);
      if (play) {
        play();
      }
      loading.setVisible(false);
    });
    player.setOnEndOfMedia(this::playNext);
  }

  /**
   * ??????
   */
  @FXML
  private void download() {
    if (bookListView.getSelectionModel().isEmpty()) {
      return;
    }
    AudioBook book = bookListView.getSelectionModel().getSelectedItem();
    loadCache(book);
    BookBundle bundle = new BookBundle(book.toNovel(), currentBook.getRule());
    bundle.getNovel().setChapters(book.getToc());
    navigation.navigate(DownloadManagerView.class, new SidebarNavigateBundle().put(DownloadManagerView.BUNDLE_DOWNLOAD_KEY, bundle));
  }

  /**
   * ???????????????????????????
   */
  @FXML
  private void openBrowser() {
    if (currentBook != null) {
      DesktopUtils.openBrowse(currentBook.getUrl());
    }
  }

  /**
   * ???????????????????????? ??????????????????
   *
   * @param audioUrlHandler  ???????????? ?????????<?????????????????????????????????>
   * @param onSuccessHandler ???????????? FX??????
   * @param <T>              ??????????????????
   */
  private <T> void withAudioUrl(BiFunction<String, String, T> audioUrlHandler, Consumer<T> onSuccessHandler) {
    MultipleSelectionModel<Chapter> selectionModel = tocListView.getSelectionModel();
    if (selectionModel.isEmpty()) {
      return;
    }
    AudioBook novel = bookListView.getSelectionModel().getSelectedItem();
    Chapter chapter = selectionModel.getSelectedItem();
    String url = chapter.getUrl();
    NovelSpider spider = new NovelSpider(novel.getRule());
    TaskFactory.create(() -> {
      String audioUrl = spider.content(url);
      return audioUrlHandler.apply(url, audioUrl);
    }).onSuccess(onSuccessHandler)
      .onFailed(e -> Toast.error("??????????????????"))
      .start();
  }

  /**
   * ??????????????????
   */
  @FXML
  private void checkAudioEffective() {
    withAudioUrl((chapterUrl, audioUrl) -> {
      AtomicBoolean validate = new AtomicBoolean(false);
      try {
        RequestParams params = RequestParams.create(audioUrl);
        params.addHeader(RequestParams.REFERER, chapterUrl);
        validate.set(Http.validate(params));
      } catch (Exception e) {
        log.warn("??????????????????: ?????????{} ??????:{}", chapterUrl, audioUrl, e);
      }
      return validate.get();
    }, validate -> {
      if (Boolean.TRUE.equals(validate)) {
        Toast.success("????????????");
      } else {
        Toast.error("????????????");
      }
    });
  }

  /**
   * ???????????????
   */
  @FXML
  private void openChapterLinkBrowser() {
    String url = tocListView.getSelectionModel().getSelectedItem().getUrl();
    if (UrlUtils.isHttpUrl(url)) {
      DesktopUtils.openBrowse(url);
    }
  }

  /**
   * ??????????????????
   */
  @FXML
  private void copyAudioLink() {
    withAudioUrl((chapterUrl, audioUrl) -> audioUrl, audioUrl -> {
      DesktopUtils.copy(audioUrl);
      Toast.success("????????????");
    });
  }

  /**
   * ????????????????????????
   */
  private class ProgressChangeListener implements InvalidationListener {

    @Override
    public void invalidated(Observable observable) {
      if (player.getCurrentTime().lessThanOrEqualTo(player.getStopTime())) {
        double current = player.getCurrentTime().toSeconds();
        double total = player.getStopTime().toSeconds();
        currentTime.setText(TimeUtil.secondToTime(current));
        double to = total * progress.getValue();
        if (Math.abs(current - to) < 1) {
          progress.setValue(current / total);
        }
        // ????????????
        currentBook.setCurrentProgress(progress.getValue());
      }
    }
  }
}
