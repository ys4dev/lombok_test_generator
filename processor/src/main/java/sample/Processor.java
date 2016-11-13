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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;
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

    private Predicate<String> testFields(TypeElement element) {
        EqualsAndHashCode annotation = element.getAnnotation(EqualsAndHashCode.class);
        if (annotation == null) {
            return name -> true;
        }

        String[] of = annotation.of();
        if (of != null && of.length > 0) {
            Arrays.sort(of);
            return name -> Arrays.binarySearch(of, name) >= 0;
        }
        String[] exclude = annotation.exclude();
        if (exclude != null && exclude.length > 0) {
            Arrays.sort(exclude);
            return name -> Arrays.binarySearch(exclude, name) < 0;
        }
        return name -> true;
    }

    private List<TypeAndName> fields(TypeElement element) {
        Predicate<String> testFields = testFields(element);
        return element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .map(e -> (VariableElement)e)
                .map(e -> new TypeAndName(e.asType(), e.getSimpleName(), testValueOfType(e.asType(), e.getSimpleName(), testFields)))
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

        List<TypeAndName> fields = fields(element);
        List<List<PlaceholderValue>> values = values(fields);

        for (List<PlaceholderValue> value : values) {
            for (List<PlaceholderValue> value2 : values) {
                addNewObjectStatement(builder, element, "obj1", value);
                addNewObjectStatement(builder, element, "obj2", value2);
                String eq = value == value2 ? "" : "Not";
                builder.addStatement("$T.assert" + eq + "Equals(obj1, obj2)", Assert.class);
            }
        }

        builder.addStatement("$T.assertEquals(obj1, obj1)", Assert.class);
        builder.addStatement("$T.assertNotEquals(obj1, null)", Assert.class);

        return builder.build();
    }

    private List<PlaceholderValue> valuesNull(List<TypeAndName> fields) {
        List<TypeAndName> nullValues = fields.stream().map(e -> {
            int size = e.getValues().size();
            return new TypeAndName(e.getType(), e.getName(), e.getValues().subList(size - 1, size));
        }).collect(Collectors.toList());
        List<List<PlaceholderValue>> values = values(nullValues);
        return values.get(values.size() - 1);
    }

    private List<List<PlaceholderValue>> values(List<TypeAndName> fields) {
        List<List<PlaceholderValue>> result = new ArrayList<>();
        values(fields, new ArrayList<>(), result, false);
        return result;
    }

    private void values(List<TypeAndName> fields, List<PlaceholderValue> buf, List<List<PlaceholderValue>> result, boolean containsAlt) {
        if (fields.isEmpty()) {
            result.add(buf);
            return;
        }

        TypeAndName head = fields.get(0);
        List<PlaceholderValue> values = head.getValues();

        if (containsAlt) {
            ArrayList<PlaceholderValue> tmp = new ArrayList<>(buf);
            tmp.add(values.get(0));
            values(fields.subList(1, fields.size()), tmp, result, true);
        } else {
            boolean first = true;
            for (PlaceholderValue value : values) {
                ArrayList<PlaceholderValue> tmp = new ArrayList<>(buf);
                tmp.add(value);
                values(fields.subList(1, fields.size()), tmp, result, ! first);
                first = false;
            }
        }
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

        List<TypeAndName> fields = fields(element);
        List<List<PlaceholderValue>> values = values(fields);

        addNewObjectStatement(builder, element, "obj1", values.get(0));
        addNewObjectStatement(builder, element, "obj2", values.get(0));
        builder.addStatement("$T.assertEquals(obj1.hashCode(), obj2.hashCode())", Assert.class);

        List<PlaceholderValue> nullValues = valuesNull(fields);

        addNewObjectStatement(builder, element, "obj1", nullValues);
        addNewObjectStatement(builder, element, "obj2", nullValues);
        builder.addStatement("$T.assertEquals(obj1.hashCode(), obj2.hashCode())", Assert.class);

        return builder.build();
    }

    private void addNewObjectStatement(CodeBlock.Builder builder, TypeElement element, String objName, List<PlaceholderValue> values) {
        ClassName className = ClassName.get(element);
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

    private List<PlaceholderValue> testValueOfType(TypeMirror type, Name name, Predicate<String> testFields) {
        List<PlaceholderValue> values = testValueOfType(type, name);
        if (! testFields.test(name.toString())) {
            return values.subList(0, 1);
        }
        return values;
    }

    private List<PlaceholderValue> testValueOfType(TypeMirror type, Name name) {
        switch (type.toString().split("<")[0]) {
            case "java.lang.String":
                return Arrays.asList(
                        new PlaceholderValue("$S", name, String.format("%s%d", name.toString(), 1)),
                        new PlaceholderValue("$S", name, String.format("%s%d", name.toString(), 2)),
                        new PlaceholderValue("$L", name, null)
                );
            case "int":
                return Arrays.asList(
                        new PlaceholderValue("$L", name, 1),
                        new PlaceholderValue("$L", name, 2)
                );
            case "java.lang.Integer":
                return Arrays.asList(
                        new PlaceholderValue("$L", name, 1),
                        new PlaceholderValue("$L", name, 2),
                        new PlaceholderValue("$L", name, null)
                );
            case "long":
                return Arrays.asList(
                        new PlaceholderValue("$L", name, 1L),
                        new PlaceholderValue("$L", name, 2L)
                );
            case "java.lang.Long":
                return Arrays.asList(
                        new PlaceholderValue("$L", name, 1L),
                        new PlaceholderValue("$L", name, 2L),
                        new PlaceholderValue("$L", name, null)
                );
            case "java.util.Date":{
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createDate(1));
                MethodSpec factory2 = factories.computeIfAbsent(new FactoryKey(element, 2), e -> createDate(2));

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$N()", name, factory2),
                        new PlaceholderValue("$L", name, null)
                );
            }
            case "java.time.LocalDate": {
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createLocalDate(1));
                MethodSpec factory2 = factories.computeIfAbsent(new FactoryKey(element, 2), e -> createLocalDate(2));

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$N()", name, factory2),
                        new PlaceholderValue("$L", name, null)
                );
            }
            case "java.time.LocalTime": {
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createLocalTime(1));
                MethodSpec factory2 = factories.computeIfAbsent(new FactoryKey(element, 2), e -> createLocalTime(2));

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$N()", name, factory2),
                        new PlaceholderValue("$L", name, null)
                );
            }
            case "java.time.LocalDateTime": {
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createLocalDateTime(1));
                MethodSpec factory2 = factories.computeIfAbsent(new FactoryKey(element, 2), e -> createLocalDateTime(2));

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$N()", name, factory2),
                        new PlaceholderValue("$L", name, null)
                );
            }
            case "java.util.List": {
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createList());

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$L", name, null)
                );
            }
            case "java.util.Set": {
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createSet());

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$L", name, null)
                );
            }
            case "java.util.Map": {
                DeclaredType dtype = (DeclaredType) type;
                TypeElement element = (TypeElement) dtype.asElement();
                MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createMap());

                return Arrays.asList(
                        new PlaceholderValue("$N()", name, factory1),
                        new PlaceholderValue("$L", name, null)
                );
            }
            default:
                if (type instanceof DeclaredType) {
                    DeclaredType dtype = (DeclaredType) type;
                    TypeElement element = (TypeElement) dtype.asElement();
                    if (targetElements.contains(element)) {
                        MethodSpec factory1 = factories.computeIfAbsent(new FactoryKey(element, 1), e -> createFactoryFor(e.getElement(), 1));
                        MethodSpec factory2 = factories.computeIfAbsent(new FactoryKey(element, 2), e -> createFactoryFor(e.getElement(), 2));
                        return Arrays.asList(
                                new PlaceholderValue("$N()", name, factory1),
                                new PlaceholderValue("$N()", name, factory2),
                                new PlaceholderValue("$L", name, null)
                        );
                    }
                }
                return Arrays.asList(new PlaceholderValue("$L", name, null));
        }
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

    private MethodSpec createList() {
        return MethodSpec.methodBuilder("createList")
                .returns(List.class)
                .addStatement("return $T.asList()", Arrays.class)
                .build();
    }

    private MethodSpec createSet() {
        return MethodSpec.methodBuilder("createSet")
                .returns(Set.class)
                .addStatement("return $T.emptySet()", Collections.class)
                .build();
    }

    private MethodSpec createMap() {
        return MethodSpec.methodBuilder("createMap")
                .returns(Map.class)
                .addStatement("return $T.emptyMap()", Collections.class)
                .build();
    }

    private MethodSpec createDate(int n) {
        return MethodSpec.methodBuilder("createDate" + n)
                .returns(Date.class)
                .addStatement("$T date = new $T()", Date.class, Date.class)
                .addStatement("date.setTime($LL)", System.currentTimeMillis() + n * 1000)
                .addStatement("return date")
                .build();
    }

    private MethodSpec createLocalTime(int n) {
        return MethodSpec.methodBuilder("createLocalTime" + n)
                .returns(LocalTime.class)
                .addStatement("return LocalTime.ofSecondOfDay($LL)", n * 1000)
                .build();
    }

    private MethodSpec createLocalDate(int n) {
        return MethodSpec.methodBuilder("createLocalDate" + n)
                .returns(LocalDate.class)
                .addStatement("return LocalDate.ofEpochDay($LL)", System.currentTimeMillis() / 1000 / 60 / 60 / 24 + n)
                .build();
    }

    private MethodSpec createLocalDateTime(int n) {
        return MethodSpec.methodBuilder("createLocalDateTime" + n)
                .returns(LocalDateTime.class)
                .addStatement("return LocalDateTime.of(LocalDate.ofEpochDay($LL), LocalTime.ofSecondOfDay($LL))",
                        System.currentTimeMillis() / 1000 / 60 / 60 / 24 + n, n * 1000)
                .build();
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
        List<PlaceholderValue> values;
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
