package com.unclezs.novel.app.main.views.components.cell;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.unclezs.novel.analyzer.model.Novel;
import com.unclezs.novel.analyzer.util.StringUtils;
import com.unclezs.novel.app.framework.components.LoadingImageView;
import com.unclezs.novel.app.framework.components.Tag;
import com.unclezs.novel.app.framework.components.cell.BaseListCell;
import com.unclezs.novel.app.framework.support.LocalizedSupport;
import com.unclezs.novel.app.framework.util.NodeHelper;
import com.unclezs.novel.app.framework.util.ResourceUtils;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索小说结果
 *
 * @author blog.unclezs.com
 * @date 2021/4/16 22:39
 */
public class BookListCell extends BaseListCell<Novel> implements LocalizedSupport {

  public static final Image NO_COVER = new Image(ResourceUtils.externalForm("assets/images/no-cover.jpg"), true);
  private final HBox cell = NodeHelper.addClass(new HBox(), "cell");
  private final Label title = NodeHelper.addClass(new Label(), "title");
  private final Label author = new Label(CharSequenceUtil.EMPTY, new Label(localized("novel.author").concat(StrUtil.COLON)));
  private final Label desc = new Label(CharSequenceUtil.EMPTY, new Label(localized("novel.desc").concat(StrUtil.COLON)));
  private final Label latestChapter = new Label(CharSequenceUtil.EMPTY, new Label(localized("novel.chapter.latest").concat(StrUtil.COLON)));
  private final HBox tags = NodeHelper.addClass(new HBox(), "tags");
  private final LoadingImageView cover;

  public BookListCell(ListView<Novel> listView) {
    this.cover = new LoadingImageView(NO_COVER, 65, 90);
    desc.prefWidthProperty().bind(listView.widthProperty().subtract(100));
    cell.getChildren().addAll(cover, new VBox(title, author, latestChapter, desc, tags));
  }


  @Override
  protected void updateItem(Novel novel) {
    init(novel);
    if (getGraphic() == null) {
      setGraphic(cell);
    }
  }

  /**
   * 初始化
   *
   * @param novel 小说信息
   */
  private void init(Novel novel) {
    // 更新封面
    cover.setImage(novel.getCoverUrl());
    // 更新小说信息
    String unknown = localized("unknown");
    this.title.setText(CharSequenceUtil.blankToDefault(novel.getTitle(), unknown).replace(StringUtils.LF, StringUtils.EMPTY));
    this.author.setText(CharSequenceUtil.blankToDefault(novel.getAuthor(), unknown).replace(StringUtils.LF, StringUtils.EMPTY));
    this.desc.setText(CharSequenceUtil.blankToDefault(novel.getIntroduce(), localized("none")).replace(StringUtils.LF, StringUtils.EMPTY));
    this.latestChapter.setText(CharSequenceUtil.blankToDefault(novel.getLatestChapterName(), unknown).replace(StringUtils.LF, StringUtils.EMPTY));
    // 更新标签
    List<Tag> novelTags = new ArrayList<>();
    // 播音
    if (CharSequenceUtil.isNotBlank(novel.getBroadcast())) {
      novelTags.add(new Tag(novel.getBroadcast()));
    }
    // 连载状态
    if (CharSequenceUtil.isNotBlank(novel.getState())) {
      novelTags.add(new Tag(novel.getState()));
    }
    // 分类
    if (CharSequenceUtil.isNotBlank(novel.getCategory())) {
      novelTags.add(new Tag(novel.getCategory()));
    }
    // 字数
    if (CharSequenceUtil.isNotBlank(novel.getWordCount())) {
      novelTags.add(new Tag(novel.getWordCount()));
    }
    // 更新时间
    if (CharSequenceUtil.isNotBlank(novel.getUpdateTime())) {
      novelTags.add(new Tag(novel.getUpdateTime()));
    }
    tags.getChildren().setAll(novelTags);
  }
}
