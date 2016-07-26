package com.oroplatform.idea.oroplatform.intellij.codeAssist;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiReferenceProvider;
import com.oroplatform.idea.oroplatform.schema.PhpClass;
import com.oroplatform.idea.oroplatform.schema.PropertyPath;

public interface ReferenceProviders {
    PsiReferenceProvider filePath(InsertHandler<LookupElement> insertHandler);
    PsiReferenceProvider phpCallback(InsertHandler<LookupElement> insertHandler);
    PsiReferenceProvider phpClass(PhpClass phpClass, InsertHandler<LookupElement> insertHandler);
    PsiReferenceProvider phpField(PropertyPath classPropertyPath, InsertHandler<LookupElement> insertHandler);
    PsiReferenceProvider phpMethod(String pattern, InsertHandler<LookupElement> insertHandler);
}
