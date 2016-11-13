package sample;

import com.squareup.javapoet.*;
import lombok.*;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

import static sample.Processor.Indexed.withIndex;

/**
 * Created by sakura on 2016/11/08.
 *
 */
public class Processor extends AbstractProcessor {

    private int round;
    private Set<Element> targetElements;
    private Map<FactoryKey, MethodSpec> factories;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> types = new HashSet<>();
        types.add("lombok.*");
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("Round : " + (this.round++));

        targetElements = new HashSet<>();

        targetElements.addAll(roundEnv.getElementsAnnotatedWith(ToString.class));
        targetElements.addAll(roundEnv.getElementsAnnotatedWith(Data.class));
        targetElements.addAll(roundEnv.getElementsAnnotatedWith(Value.class));
        targetElements.addAll(roundEnv.getElementsAnnotatedWith(EqualsAndHashCode.class));

        Filer filer = super.processingEnv.getFiler();
        try {
            for (Element element : targetElements) {
                factories = new HashMap<>();
                String qname = element.toString() + "_LombokTest";
                PackageAndClass name = PackageAndClass.of(qname);
                TypeSpec.Builder builder = TypeSpec.classBuilder(name.getClassName())
                        .addModifiers(Modifier.PUBLIC);

                if (hasToString(element)) {
                    builder.addMethod(toStringTestSpec((TypeElement) element));
                }
                if (hasHashCodeEquals(element)) {
                    builder.addMethod(equalsTestSpec((TypeElement) element));
                    builder.addMethod(hashCodeTestSpec((TypeElement) element));
                }

                builder.addMethods(factories.values());

                TypeSpec typeSpec = builder.build();

                String packageName = name.getPackageName();
                JavaFile.builder(packageName, typeSpec).build().writeTo(filer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean hasHashCodeEquals(Element element) {
        return element.getAnnotation(EqualsAndHashCode.class) != null ||
                element.getAnnotation(Data.class) != null ||
                element.getAnnotation(Value.class) != null;

    }

    private boolean hasToString(Element element) {
        return element.getAnnotation(ToString.class) != null ||
                element.getAnnotation(Data.class) != null ||
                element.getAnnotation(Value.class) != null;
    }

    private MethodSpec toStringTestSpec(TypeElement element) {
        return MethodSpec.methodBuilder("testToString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addCode(toStringBody(element))
                .build();
    }

    private CodeBlock toStringBody(TypeElement element) {
        ClassName className = ClassName.get(element);
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.addStatement("$T obj", className);
        addNewObjectStatement(builder, element, "obj", Integer.MAX_VALUE);
        return builder
                .addStatement("$T.assertNotNull(obj.toString())", Assert.class)
                .build();
    }

    private boolean needsFillConstructor(TypeElement element) {
        return element.getAnnotation(Value.class) != null ||
                (element.getAnnotation(NoArgsConstructor.class) != null && element.getAnnotation(AllArgsConstructor.class) == null);
    }

    private List<TypeAndName> fields(TypeElement element) {
        return element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .map(e -> (VariableElement)e)
                .map(e -> new TypeAndName(e.asType(), e.getSimpleName()))
                .collect(Collectors.toList());
    }

    private MethodSpec equalsTestSpec(TypeElement element) {
        return MethodSpec.methodBuilder("testEquals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addCode(equalsBody(element))
                .build();
    }

    private CodeBlock equalsBody(TypeElement element) {
        ClassName className = ClassName.get(element);
        CodeBlock.Builder builder = CodeBlock.builder()
                .addStatement("$T obj1", className)
                .addStatement("$T obj2", className);

        List<? extends Element> children = element.getEnclosedElements();
        for (int i = children.size() - 1; i >= 0; i--) {
            addNewObjectStatement(builder, element, "obj1", children.size());
            addNewObjectStatement(builder, element, "obj2", i);
            String eq = i == children.size() - 1 ? "" : "Not";
            builder.addStatement("$T.assert" + eq + "Equals(obj1, obj2)", Assert.class);
        }

        builder.addStatement("$T.assertEquals(obj1, obj1)", Assert.class);
        builder.addStatement("$T.assertNotEquals(obj1, null)", Assert.class);

        return builder.build();
    }

    private MethodSpec hashCodeTestSpec(TypeElement element) {
        return MethodSpec.methodBuilder("testHashCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addCode(hashCodeBody(element))
                .build();
    }

    private CodeBlock hashCodeBody(TypeElement element) {
        ClassName className = ClassName.get(element);
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.addStatement("$T obj1", className);
        builder.addStatement("$T obj2", className);
        addNewObjectStatement(builder, element, "obj1", Integer.MAX_VALUE);
        addNewObjectStatement(builder, element, "obj2", Integer.MAX_VALUE);
        return builder
                .addStatement("$T.assertEquals(obj1.hashCode(), obj2.hashCode())", Assert.class)
                .build();
    }

    private void addNewObjectStatement(CodeBlock.Builder builder, TypeElement element, String objName, int n) {
        ClassName className = ClassName.get(element);
        List<Indexed<TypeAndName>> fields = withIndex(fields(element));
        List<PlaceholderValue> values = fields.stream().map(indexed -> {
            int valueIndex = indexed.getIndex() >= n ? 2 : 1;
            return testValueOfType2(indexed.getValue().getType(), indexed.getValue().getName(), valueIndex);
        }).collect(Collectors.toList());

        if (needsFillConstructor(element)) {
            String format = values.stream().map(v -> v.getPlaceholder()).collect(Collectors.joining(", "));
            Object[] objects = values.stream().map(v -> v.getValue()).toArray();
            builder.add(objName + " = new $T(", className);
            builder.add(format + ");\n", objects);
        } else {
            builder.addStatement(objName + " = new $T()", className);
            for (PlaceholderValue value : values) {
                builder.addStatement(objName + ".set" + setter(value.getName()) + "(" + value.getPlaceholder() + ")", value.getValue());
            }
        }
    }

    private String setter(Name name) {
        String s = name.toString();
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private PlaceholderValue testValueOfType2(TypeMirror type, Name name, int n) {
        switch (type.toString()) {
            case "java.lang.String":
                return new PlaceholderValue("$S", name, String.format("%s%d", name.toString(), n));
            case "int":
            case "java.lang.Integer":
                return new PlaceholderValue("$L", name, n);
            case "long":
            case "java.lang.Long":
                return new PlaceholderValue("$L", name, (long) n);
            default:
                if (type instanceof DeclaredType) {
                    DeclaredType dtype = (DeclaredType) type;
                    TypeElement element = (TypeElement) dtype.asElement();
                    if (targetElements.contains(element)) {
                        MethodSpec factory = factories.computeIfAbsent(new FactoryKey(element, n), e -> createFactoryFor(e.getElement(), n));
                        return new PlaceholderValue("$N()", name, factory);
                    }
                }
                return new PlaceholderValue("$L", name, null);
        }
    }


    private MethodSpec createFactoryFor(TypeElement e, int n) {
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.addStatement("$T obj", ClassName.get(e));
        addNewObjectStatement(builder, e, "obj", n);
        builder.addStatement("return obj");

        return MethodSpec.methodBuilder("create" + e.getSimpleName() + n)
                .returns(ClassName.get(e))
                .addCode(builder.build())
                .build();
    }

    @Value
    private static class PackageAndClass {
        String packageName;
        String className;

        static PackageAndClass of(String name) {
            int index = name.lastIndexOf('.');
            if (index >= 0) {
                return new PackageAndClass(name.substring(0, index), name.substring(index + 1));
            } else {
                return new PackageAndClass("", name);
            }
        }
    }

    @Value
    private static class TypeAndName {
        TypeMirror type;
        Name name;
    }

    @Value
    static class Indexed<T> {
        T value;
        int index;

        static <T> List<Indexed<T>> withIndex(List<T> list) {
            List<Indexed<T>> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                result.add(new Indexed<>(list.get(i), i));
            }
            return result;
        }
    }

    @Value
    private static class PlaceholderValue {
        String placeholder;
        Name name;
        Object value;
    }

    @Value
    private static class FactoryKey {
        TypeElement element;
        int n;
    }
}
