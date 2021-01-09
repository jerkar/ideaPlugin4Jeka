package dev.jeka.ide.intellij.extension;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.*;
import dev.jeka.ide.intellij.action.JekaRunCommandAction;
import dev.jeka.ide.intellij.common.JekaIcons;
import dev.jeka.ide.intellij.common.PsiClassHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;


public class JekaRunLineMarkerContributor extends RunLineMarkerContributor {

    @Nullable
    @Override
    public Info getInfo(@NotNull PsiElement psiEl) {
        if (!isIdentifier(psiEl)) {
            return null;
        }
        PsiElement psiParentEl = psiEl.getParent();
        if (! (psiParentEl instanceof PsiMethod)) {
            return null;
        }
        PsiMethod psiMethod = (PsiMethod) psiParentEl;
        PsiClass psiClass = psiMethod.getContainingClass();
        if (!PsiClassHelper.isExtendingJkCommandSet(psiClass)) {
            return null;
        }
        if (psiMethod.hasParameters() || psiMethod.isConstructor()
                || psiMethod.getModifierList().hasExplicitModifier("static")
                || !psiMethod.getModifierList().hasExplicitModifier("public")
                || !psiMethod.getReturnType().equals(PsiType.VOID)) {
            return null;
        }
        final AnAction[] actions = new AnAction[] {JekaRunCommandAction.RUN_JEKA_INSTANCE,
                JekaRunCommandAction.DEBUG_JEKA_INSTANCE};
        Function<PsiElement, String> tooltipProvider = el -> "Run '" + el.getText() + "' as Jeka command";
        return new Info(JekaIcons.JEKA_RUN, actions, tooltipProvider);
    }

    protected boolean isIdentifier(PsiElement e) {
        return e instanceof PsiIdentifier;
    }
}