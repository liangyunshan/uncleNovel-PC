package com.unclezs.novel.app.packager.subtask.windows;

import com.unclezs.novel.app.packager.subtask.BaseSubTask;
import com.unclezs.novel.app.packager.util.ExecUtils;
import com.unclezs.novel.app.packager.util.VelocityUtils;
import java.io.File;

/**
 * 使用exe4j创建exe文件
 *
 * @author blog.unclezs.com
 * @date 2021/4/5 23:19
 */
public class CreateExe extends BaseSubTask {

  public CreateExe() {
    super("创建EXE");
  }

  @Override
  protected Object run() throws Exception {
    File config = new File(packager.getAssetsFolder(), packager.getName().concat(".exe4j"));
    VelocityUtils.render("/packager/windows/exe4j.vm", config, packager);
    ExecUtils.create("exe4jc").add(config).exec();
    return new File(packager.getAppFolder(), packager.getName().concat(".exe"));
  }
}
