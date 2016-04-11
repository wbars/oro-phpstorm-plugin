package com.oroplatform.idea.oroplatform.intellij.codeAssist.yml;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceRegistrar;
import com.oroplatform.idea.oroplatform.schema.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

class PhpReferenceVisitor extends YmlVisitor {

    private final PsiReferenceRegistrar registrar;

    PhpReferenceVisitor(PsiReferenceRegistrar registrar, ElementPattern<? extends PsiElement> capture, VisitingContext context) {
        super(capture, context);
        this.registrar = registrar;
    }

    @Override
    protected Visitor nextVisitor(ElementPattern<? extends PsiElement> capture, VisitingContext context) {
        return new PhpReferenceVisitor(registrar, capture, context);
    }

    @Override
    protected void handleContainer(Container container, ElementPattern<? extends PsiElement> capture) {
    }

    @Override
    public void visitOneOf(OneOf oneOf) {
        for(Element element : oneOf.getElements()) {
            element.accept(this);
        }
    }

    @Override
    public void visitLiteralPhpClassValue(Scalar.PhpClass phpClass) {
        registrar.registerReferenceProvider(
            psiElement().andOr(
                capture,
                psiElement().withParent(capture)
            ),
            new PhpClassReferenceProvider(phpClass, insertHandler)
        );
    }

    @Override
    public void visitLiteralPhpMethodValue(Scalar.PhpMethod phpMethod) {
        registrar.registerReferenceProvider(capture, new PhpMethodReferenceProvider(phpMethod));
    }

    @Override
    public void visitLiteralPhpFieldValue(Scalar.PhpField phpField) {
        registrar.registerReferenceProvider(capture, new PhpFieldReferenceProvider(phpField));
    }
}
