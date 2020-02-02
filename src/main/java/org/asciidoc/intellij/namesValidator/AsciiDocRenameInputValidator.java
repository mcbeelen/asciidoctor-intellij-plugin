package org.asciidoc.intellij.namesValidator;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.asciidoc.intellij.psi.AsciiDocAttributeDeclarationName;
import org.asciidoc.intellij.psi.AsciiDocIncludeTagInDocument;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class AsciiDocRenameInputValidator implements RenameInputValidator {
  private final ElementPattern<? extends PsiElement> myPattern = PlatformPatterns.or(
    PlatformPatterns.psiElement(AsciiDocIncludeTagInDocument.class),
    PlatformPatterns.psiElement(AsciiDocAttributeDeclarationName.class)
  );
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z0-9_-]*");

  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return myPattern;
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return IDENTIFIER_PATTERN.matcher(newName).matches();
  }
}
