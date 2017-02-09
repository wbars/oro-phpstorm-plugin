package com.oroplatform.idea.oroplatform.intellij.indexes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpReturn;
import com.oroplatform.idea.oroplatform.symfony.Service;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.oroplatform.idea.oroplatform.Functions.toStream;

public class ServicesIndex {

    private final Project project;

    private ServicesIndex(Project project) {
        this.project = project;
    }

    public static ServicesIndex instance(Project project) {
        return new ServicesIndex(project);
    }

    public Collection<String> findConditionNames() {
        return getServiceAliasesByTag("oro_action.condition");
    }

    public Collection<String> findActionNames() {
        return getServiceAliasesByTag("oro_action.action");
    }

    private Collection<String> getServiceAliasesByTag(String tagName) {
        return FileBasedIndex.getInstance().getAllKeys(ServicesFileBasedIndex.KEY, project).stream()
            .flatMap(serviceId -> getServices(serviceId).stream())
            .flatMap(service -> service.getTags().stream())
            .filter(tag -> tagName.equals(tag.getName()))
            .flatMap(tag -> toStream(tag.getAlias()))
            .flatMap(alias -> Stream.of(alias.split("\\|")))
            .collect(Collectors.toList());
    }

    public Collection<String> findFormTypes() {
        return getServiceAliasesByTag("form.type");
    }

    public Collection<String> findApiFormTypes() {
        final Collection<String> standardApiFormTypes = findStandardApiFormTypes().stream()
            .map(formType -> formType.replace("form.type.", "")).collect(Collectors.toSet());

        return Stream.concat(
            findFormTypes().stream()
                .filter(standardApiFormTypes::contains),
            getServiceAliasesByTag("oro.api.form.type").stream()
        ).collect(Collectors.toList());
    }

    private Collection<String> findStandardApiFormTypes() {
        return FileBasedIndex.getInstance().getAllKeys(StandardApiFormTypeFileBasedIndex.KEY, project);
    }

    public Collection<String> findServices() {
        return FileBasedIndex.getInstance().getAllKeys(ServicesFileBasedIndex.KEY, project);
    }

    public Collection<String> findServices(Predicate<Service> predicate) {
        final Collection<String> serviceNames = FileBasedIndex.getInstance().getAllKeys(ServicesFileBasedIndex.KEY, project);

        return serviceNames.stream()
            .filter(serviceName -> getServices(serviceName).stream().anyMatch(predicate))
            .collect(Collectors.toList());
    }

    @NotNull
    private Collection<Service> getServices(String serviceName) {
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final Collection<Service> services = new LinkedList<>();
        FileBasedIndex.getInstance().processValues(ServicesFileBasedIndex.KEY, serviceName, null, ((file, value) -> {
            services.add(value);
            return false;
        }), scope);
        return services;
    }

    public Optional<Service> findService(String id) {
        return FileBasedIndex.getInstance().getValues(ServicesFileBasedIndex.KEY, id, GlobalSearchScope.allScope(project)).stream()
            .findFirst();
    }

    public Collection<String> findMassActionProviders() {
        return getServiceAliasesByTag("oro_action.datagrid.mass_action_provider");
    }

    public Optional<String> findParameterValue(String name) {
        return FileBasedIndex.getInstance().getValues(ServiceParametersFileBasedIndex.KEY, name, GlobalSearchScope.allScope(project)).stream()
            .findFirst();
    }

    public Collection<WorkflowScope> findWorkflowScopes() {
        final PhpIndex phpIndex = PhpIndex.getInstance(project);

        return FileBasedIndex.getInstance().getAllKeys(ServicesFileBasedIndex.KEY, project).stream()
            .flatMap(serviceId -> getServices(serviceId).stream())
            .filter(this::isWorkflowScope)
            .flatMap(service -> toStream(service.getClassName()))
            .map(serviceClassName -> {
                return serviceClassName.getServiceParameter()
                    .flatMap(this::findParameterValue)
                    .orElse(serviceClassName.getClassName());
            })
            .flatMap(className -> phpIndex.getClassesByFQN(className).stream())
            .flatMap(phpClass -> toStream(findWorkflowScope(phpClass)))
            .collect(Collectors.toList());
    }

    private boolean isWorkflowScope(Service service) {
        return service.getTags().stream().anyMatch(tag -> "oro_scope.provider".equals(tag.getName()) && tag.get("scopeType").filter("workflow_definition"::equals).isPresent());
    }

    private Optional<WorkflowScope> findWorkflowScope(PhpClass phpClass) {
        return phpClass.getMethods().stream()
            .filter(method -> "getCriteriaField".equals(method.getName()))
            .flatMap(method -> PsiTreeUtil.collectElementsOfType(method, PhpReturn.class).stream())
            .flatMap(phpReturn -> toStream(phpReturn.getArgument()))
            .flatMap(argument -> {
                if(argument instanceof ClassConstantReference && ((ClassConstantReference) argument).resolve() instanceof Field) {
                    final Field classConstant = (Field) ((ClassConstantReference) argument).resolve();
                    return toStream(classConstant.getDefaultValue()).map(PsiElement::getText);
                } else {
                    return Stream.of(argument.getText());
                }
            })
            .map(StringUtil::stripQuotesAroundValue)
            .map(name -> new WorkflowScope(phpClass, name))
            .findFirst();
    }

    public static class WorkflowScope {
        public final PhpClass phpClass;
        public final String name;

        private WorkflowScope(PhpClass phpClass, String name) {
            this.phpClass = phpClass;
            this.name = name;
        }
    }
}
