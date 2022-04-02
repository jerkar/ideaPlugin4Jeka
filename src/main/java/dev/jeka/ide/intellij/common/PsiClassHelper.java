package dev.jeka.ide.intellij.common;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import dev.jeka.core.tool.JkDoc;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class PsiClassHelper {

    private static final String JKBEAN_CLASS_NAME = "dev.jeka.core.tool.JkBean";

    public static boolean isExtendingJkBean(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        if (JKBEAN_CLASS_NAME.equals(psiClass.getQualifiedName())) {
            return true;
        }
        PsiClassType[] psiClassTypes = psiClass.getExtendsListTypes();
        for (PsiClassType psiClassType : psiClassTypes) {
            if (psiClassType == null) {
                return false;
            }
            PsiClass currentPsiClass = psiClassType.resolve();
            if (isExtendingJkBean(currentPsiClass)) {
                return true;
            }
        }
        return false;
    }

    public static List<PsiClass> findKBeanClasses(Module module) {
        VirtualFile rootDir = ModuleHelper.getModuleDir(module);
        if (rootDir == null) {
            return Collections.emptyList();
        }
        VirtualFile jekaDefFolder = rootDir.findFileByRelativePath(Constants.JEKA_DIR_NAME + "/" + Constants.JEKA_DEF_DIR_NAME);
        if (jekaDefFolder == null) {
            return Collections.emptyList();
        }
        PsiManager psiManager = PsiManager.getInstance(module.getProject());
        PsiDirectory jekaDir = psiManager.findDirectory(jekaDefFolder);
        return findKBeanClasses(jekaDir);
    }

    private static List<PsiClass> findKBeanClasses(PsiDirectory dir) {
        List<PsiClass> result = new LinkedList<>();
        for (PsiFile psiFile : dir.getFiles()) {
            if (psiFile instanceof PsiJavaFile) {
                PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
                for (PsiClass psiClass : psiJavaFile.getClasses()) {
                    if (PsiClassHelper.isExtendingJkBean(psiClass)) {
                        result.add(psiClass);
                    }
                }
            }
        }
        for (PsiDirectory subDir : dir.getSubdirectories()) {
            result.addAll(findKBeanClasses(subDir));
        }
        return result;
    }

    public static String getJkDoc(PsiJvmModifiersOwner psiClass) {
        PsiAnnotation annotation = psiClass.getAnnotation(JkDoc.class.getName());
        if (annotation == null) {
            return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null || value.getText() == null) {
            return null;
        }
        String text = value.getText();
        if (text.startsWith("{")) {
            text = text.substring(1);
        }
        if (text.startsWith("\"")) {
            text = text.substring(1);
        }
        if (text.endsWith("}")) {
            text = text.substring(0, text.length()-1);
        }
        if (text.endsWith("\"")) {
            text = text.substring(0, text.length()-1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length()-1);
        }
        return text;
    }


}
