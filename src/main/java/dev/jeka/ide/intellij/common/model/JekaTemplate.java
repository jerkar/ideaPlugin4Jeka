package dev.jeka.ide.intellij.common.model;

import com.intellij.ui.CollectionListModel;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsString;
import lombok.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


@Getter
@Setter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class JekaTemplate {

    private static final String SPRINGBOOT_MODULE = "dev.jeka:springboot-plugin";

    public static final JekaTemplate blank() {
        return JekaTemplate.builder()
                .name("blank")
                .commandArgs("")
                .description("Template for automation tasks that does not need to build projects.")
                .build();
    }

    public static final JekaTemplate project() {
        return JekaTemplate.builder()
                .name("java project")
                .commandArgs("project#")
                .description("Template for building Java projects.")
                .build();
    }

    public static JekaTemplate springboot() {
        return JekaTemplate.builder()
                .name("springboot project")
                .commandArgs("@" + SPRINGBOOT_MODULE + " springboot#")
                .description("Template for building SpringBoot projects written in Java.")
                .build();
    }

    public static JekaTemplate plugin() {
        return JekaTemplate.builder()
                .name("plugin project")
                .commandArgs("project#scaffoldTemplate=PLUGIN")
                .description("Template for creating Jeka plugins.")
                .build();
    }

    public static JekaTemplate projectCodeLess() {
        return JekaTemplate.builder()
                .name("java project - code.less")
                .commandArgs("project#scaffoldTemplate=CODE_LESS scaffold#cmdExtraContent=\"_append=@dev.jeka:jacoco-plugin" +
                        " @dev.jeka:sonarqube-plugin -kb=project\\nbuild=clean project#pack" +
                        "\\nbuild_quality=clean project#pack sonarqube#run jacoco# sonarqube#logOutput=true -Dsonar.host.url=http://localhost:9000\\n\" " +
                        "scaffold#projectPropsExtraContent=\"jeka.java.version=11\"")
                .description("Template for building Java projects without needing build code.\n" +
                        "\n" +
                        "Use this template if your project is standard enough to not require \n" +
                        "any code to configure builds.\n" +
                        "\n" +
                        "You can configure the build by editing :\n" +
                        "  - cmd.properties file\n" +
                        "  - project.properties file\n" +
                        "  - dependencies.txt file")
                .build();
    }



    public static final List<JekaTemplate> builtins() {
        return JkUtilsIterable.listOf(blank(), project(), projectCodeLess(), springboot(), plugin());
    }

    private static List<String> builtinNames() {
        return builtins().stream().map(JekaTemplate::getName).collect(Collectors.toList());
    }

    @EqualsAndHashCode.Include
    String name;

    String commandArgs;

    String description;

    @Override
    public String toString() {
        return name;
    }

    public static void resetBuiltin(List<JekaTemplate> jekaTemplates) {
        List<String> builtinNames = builtinNames();
        List<JekaTemplate> toDelete = jekaTemplates.stream()
                .filter(template -> builtinNames.contains(template.getName()))
                .collect(Collectors.toList());
        toDelete.forEach(template -> jekaTemplates.remove(template));
        List<JekaTemplate> builtins = new LinkedList<>(builtins());
        Collections.reverse(builtins);
        for (JekaTemplate jekaTemplate : builtins) {
            jekaTemplates.add(0, jekaTemplate);
        }
    }

    public static void addOrReplace(CollectionListModel<JekaTemplate> jekaTemplates, JekaTemplate template) {
        int index = jekaTemplates.getElementIndex(template);
        if (index >= 0) {
            jekaTemplates.remove(index);
            jekaTemplates.add(index, template);
        } else {
            jekaTemplates.add(template);
        }
    }

    public static JekaTemplate duplicate(List<JekaTemplate> jekaTemplates, JekaTemplate template) {
        NameAndNum nameAndNum = suggestNewName(jekaTemplates, template.getName());
        JekaTemplate newTemplate = JekaTemplate.builder()
                .name(nameAndNum.name)
                .commandArgs(template.getCommandArgs())
                .description(template.getDescription())
                .build();
        int index = nameAndNum.num <= 0 ? indexOfName(jekaTemplates, template.name) + 1 : nameAndNum.num;
        jekaTemplates.add(index, newTemplate);
        return newTemplate;
    }

    public static String suggestNewName(List<JekaTemplate> templates) {
        return suggestNewName(templates, "untitled").name;
    }

    private static NameAndNum suggestNewName(List<JekaTemplate> templates, String original) {
        if (JkUtilsString.isBlank(original)) {
            original = "untitled";
        }
        if (indexOfName(templates, original) < 0) {
            return new NameAndNum(original, templates.size());
        }
        NameAndNum nameAndNum = NameAndNum.of(original);
        String name = nameAndNum.name;
        int lastIndexOfExistingName = -1;
        for (int i = 1; i < 100; i++) {
           NameAndNum nameAndNumI = new NameAndNum(name, i);
           String candidate = nameAndNumI.toString();
           int indexOfExistingName = indexOfName(templates, candidate);
           if (indexOfExistingName < 0) {
               return new NameAndNum(candidate, lastIndexOfExistingName + 1);
           } else {
               lastIndexOfExistingName = indexOfExistingName;
           }
        }
        throw new IllegalStateException("No suggest name found.");
    }

    private static int indexOfName(List<JekaTemplate> templates, String candidate) {
        for (int i=0; i < templates.size(); i++) {
            if (templates.get(i).name.equals(candidate)) {
                return i;
            }
        }
        return -1;
    }

    @Value
    private static class NameAndNum {
        String name;
        Integer num;

        static NameAndNum of(String candidate) {
            Integer num = extract(candidate);
            if (num == null) {
                return new NameAndNum(candidate, null);
            }
            String name = JkUtilsString.substringBeforeLast(candidate, "(").trim();
            return new NameAndNum(name, num);
        }

        private static Integer extract(String candidate) {
            int openIndex = candidate.lastIndexOf('(');
            int closeIndex = candidate.lastIndexOf(')');
            if (openIndex < 0 || closeIndex < openIndex+2 ) {
                return null;
            }
            String between = candidate.substring(openIndex + 1, closeIndex);
            try {
               return Integer.parseInt(between);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public String toString() {
            return name + " (" + num + ")";
        }
    }

}