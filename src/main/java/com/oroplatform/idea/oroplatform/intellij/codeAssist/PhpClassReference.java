package com.oroplatform.idea.oroplatform.intellij.codeAssist;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.completion.PhpClassLookupElement;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.oroplatform.idea.oroplatform.Icons;
import com.oroplatform.idea.oroplatform.PhpClassUtil;
import com.oroplatform.idea.oroplatform.schema.Scalar;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.util.Arrays.asList;

public class PhpClassReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final String text;
    private final InsertHandler<LookupElement> insertHandler;
    private final String rootBundlePath;
    private final String namespacePart;
    private final Scalar.PhpClass phpClass;
    private final Set<String> skippedClassNames = new HashSet<String>();

    public PhpClassReference(PsiElement psiElement, Scalar.PhpClass phpClass, @NotNull String text, InsertHandler<LookupElement> insertHandler) {
        this(psiElement, phpClass, text, insertHandler, new HashSet<String>());
    }

    public PhpClassReference(PsiElement psiElement, Scalar.PhpClass phpClass, @NotNull String text, InsertHandler<LookupElement> insertHandler, Set<String> skippedClassNames) {
        super(psiElement);
        this.phpClass = phpClass;
        this.insertHandler = insertHandler;
        this.text = text.replace("IntellijIdeaRulezzz", "").trim().replace("\\\\", "\\");
        this.rootBundlePath = myElement.getContainingFile() == null ? "" : myElement.getContainingFile().getOriginalFile().getVirtualFile().getCanonicalPath().replaceFirst("/Resources/.*", "");
        this.namespacePart = "\\"+ phpClass.getNamespacePart() +"\\";
        this.skippedClassNames.addAll(skippedClassNames);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        final PhpIndex phpIndex = PhpIndex.getInstance(myElement.getProject());
        final List<ResolveResult> results = new LinkedList<ResolveResult>();

        for(String className : resolveClassNames(phpIndex, text)) {
            for(PhpClass phpClass : phpIndex.getClassesByFQN(className)) {
                results.add(new PsiElementResolveResult(phpClass));
            }
        }

        return results.toArray(new ResolveResult[results.size()]);
    }

    private Collection<String> resolveClassNames(PhpIndex phpIndex, String text) {
        final Set<String> names = new HashSet<String>();
        names.add(text);

        if(text.contains(":")) {
            final String simpleName = PhpClassUtil.getSimpleName(text);
            final String namespaceShortcut = text.substring(0, text.indexOf(':'));
            final Collection<PhpClass> phpClasses = phpIndex.getClassesByName(simpleName);

            for(PhpClass phpClass : phpClasses) {
                final PrefixMatcher matcher = new CamelHumpMatcher(namespaceShortcut);
                if(matcher.isStartMatch(phpClass.getNamespaceName().replace("\\", ""))) {
                    names.add(phpClass.getFQN());
                }
            }
        }

        return names;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        final PhpIndex phpIndex = PhpIndex.getInstance(myElement.getProject());

        final List<LookupElement> results = new LinkedList<LookupElement>();
        for(String className : findClassNames(phpIndex)) {
            for(PhpClass phpClass : phpIndex.getClassesByName(className)) {
                final String namespaceName = phpClass.getNamespaceName();
                final boolean isClass = !phpClass.isInterface() && !phpClass.isTrait();
                if(isClass && !skippedClassNames.contains(phpClass.getFQN()) && namespaceName.contains(namespacePart) && !isIgnoredNamespace(namespaceName)) {
                    final int priority = getPriorityFor(phpClass);
                    if(this.phpClass.allowDoctrineShortcutNotation()) {
                        addEntitiesShortcutsLookups(results, phpClass, priority);
                    } else {
                        final InsertHandler<LookupElement> customInsertHandler = insertHandler != null ?
                            new ComposedInsertHandler(asList(PhpClassInsertHandler.INSTANCE, insertHandler)) : PhpClassInsertHandler.INSTANCE;
                        results.add(PrioritizedLookupElement.withPriority(new PhpClassLookupElement(phpClass, true, customInsertHandler), priority));
                    }
                }
            }
        }

        return results.toArray();
    }

    private static class ComposedInsertHandler implements InsertHandler<LookupElement> {
        private List<InsertHandler<LookupElement>> handlers = new LinkedList<InsertHandler<LookupElement>>();

        ComposedInsertHandler(List<InsertHandler<LookupElement>> handlers) {
            this.handlers.addAll(handlers);
        }

        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
            for (InsertHandler<LookupElement> handler : handlers) {
                handler.handleInsert(context, item);
            }
        }
    }

    private boolean isIgnoredNamespace(String namespaceName) {
        return namespaceName.contains("\\__CG__\\") || namespaceName.contains("\\Tests\\") || namespaceName.contains("\\Repository\\");
    }

    private int getPriorityFor(PhpClass phpClass) {
        if(isFromVendors(phpClass)) {
            return -1;
        }
        final String classRootPath = phpClass.getNamespaceName().replace("\\", "/").replaceFirst("/"+this.phpClass.getNamespacePart()+"/.*", "");
        return rootBundlePath.endsWith(classRootPath) ? 1 : 0;
    }

    private boolean isFromVendors(@NotNull PhpClass phpClass) {
        //sad null pointer checks
        return phpClass.getContainingFile() != null && phpClass.getContainingFile().getVirtualFile() != null &&
                phpClass.getContainingFile().getVirtualFile().getCanonicalPath() != null &&
                phpClass.getContainingFile().getVirtualFile().getCanonicalPath().contains("/vendor/");
    }

    private void addEntitiesShortcutsLookups(List<LookupElement> results, PhpClass phpClass, int priority) {
        final String shortcutName = PhpClassUtil.getDoctrineShortcutClassName(phpClass.getPresentableFQN());
        if(shortcutName != null) {
            results.add(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(shortcutName).withIcon(Icons.DOCTRINE).withTypeText(phpClass.getPresentableFQN()).withInsertHandler(insertHandler),
                priority
            ));
        }
    }

    private Collection<String> findClassNames(PhpIndex phpIndex) {
        final PrefixMatcher classMatcher = new CamelHumpMatcher(text);
        return phpIndex.getAllClassNames(classMatcher);
    }
}
