package com.oroplatform.idea.oroplatform.intellij.codeAssist.javascript;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.oroplatform.idea.oroplatform.intellij.codeAssist.PublicResourceWrappedStringFactory;
import com.oroplatform.idea.oroplatform.intellij.codeAssist.StringWrapperProvider;
import com.oroplatform.idea.oroplatform.intellij.codeAssist.WrappedFileReferenceProvider;
import com.oroplatform.idea.oroplatform.StringWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.oroplatform.idea.oroplatform.intellij.codeAssist.PsiElements.fileInProjectWithPluginEnabled;

public class RequireJsReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        final PsiElementPattern.Capture<JSLiteralExpression> pattern =
            psiElement(JSLiteralExpression.class).inFile(fileInProjectWithPluginEnabled())
                .and(
                    psiElement().andOr(
                        psiElement()
                            .withSuperParent(2, functionCall("require")),
                        psiElement()
                            .withSuperParent(3, functionCall("define"))
                    )
                );

        registrar.registerReferenceProvider(pattern, new WrappedFileReferenceProvider(
            new BundleJsModuleWrappedStringFactory(),
            new BundleJsRootDirsFinder())
        );
    }

    private PsiElementPattern.Capture<JSCallExpression> functionCall(String name) {
        return psiElement(JSCallExpression.class).withChild(psiElement().withText(name));
    }

    private static class BundleJsModuleWrappedStringFactory implements StringWrapperProvider {
        private final StringWrapperProvider stringWrapperProvider = new PublicResourceWrappedStringFactory();

        @Override
        public StringWrapper getStringWrapperFor(PsiElement requestElement, VirtualFile sourceDir) {
            return Optional.ofNullable(PsiManager.getInstance(requestElement.getProject()).findDirectory(sourceDir))
                //dir is used for StringWrapper building, because in this case not important is from which file completion
                //is triggered (requestElement) as in cases assets in config files, but source dir of completed element (eg. js module).
                .map(dir -> stringWrapperProvider.getStringWrapperFor(dir, sourceDir))
                .map(wrapper -> wrapper
                    .mapPrefix(prefix -> prefix.replace("bundles/", "")+"js/")
                    .mapSuffix(suffix -> ".js")
                ).orElse(new StringWrapper("", ""));
        }
    }
}
