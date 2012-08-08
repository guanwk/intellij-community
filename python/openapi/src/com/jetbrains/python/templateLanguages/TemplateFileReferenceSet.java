package com.jetbrains.python.templateLanguages;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class TemplateFileReferenceSet extends FileReferenceSet {
  private final String mySeparatorString;

  public TemplateFileReferenceSet(PsiElement element, @Nullable PsiReferenceProvider provider) {
    this(str(element), element, provider);
  }

  public TemplateFileReferenceSet(String text, PsiElement element,
                                  @Nullable PsiReferenceProvider provider) {
    super(text, element, detectShift(element, text), provider,
          SystemInfo.isFileSystemCaseSensitive);
    mySeparatorString = detectSeparator(element);
    reparse();
  }

  private static String str(PsiElement element) {
    return PythonStringUtil.stripQuotesAroundValue(element.getText());
  }

  private static int detectShift(PsiElement element, String text) {
    String elementText = element.getText();
    int from = 0;
    Pair<String, String> quotes = PythonStringUtil.getQuotes(elementText);
    if (quotes != null) {
      from = quotes.first.length();
    }

    return elementText.indexOf(text, from);
  }

  private static String detectSeparator(PsiElement element) {
    String winSeparator;
    if (PythonStringUtil.isRawString(element.getText())) {
      winSeparator = "\\";
    }
    else {
      winSeparator = "\\\\";
    }
    return str(element).contains(winSeparator) ? winSeparator : "/";
  }

  @Override
  public String getSeparatorString() {
    if (mySeparatorString == null) {
      return super.getSeparatorString();
    }
    else {
      return mySeparatorString;
    }
  }

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    List<PsiFileSystemItem> contexts = ContainerUtil.newArrayList();
    if (getPathString().startsWith("/") || getPathString().startsWith("\\")) {
      return contexts;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(getElement());
    if (module != null) {
      List<VirtualFile> templatesFolders = getRoots(module);
      for (VirtualFile folder : templatesFolders) {
        final PsiDirectory directory = PsiManager.getInstance(module.getProject()).findDirectory(folder);
        if (directory != null) {
          contexts.add(directory);
        }
      }
    }
    return contexts;
  }

  protected List<VirtualFile> getRoots(Module module) {
    return TemplatesService.getInstance(module).getTemplateFolders();
  }
}
