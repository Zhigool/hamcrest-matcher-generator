package io.github.marmer.testutils.generators.beanmatcher.generation;

import com.squareup.javapoet.*;
import io.github.marmer.testutils.generators.beanmatcher.dependencies.BasedOn;
import io.github.marmer.testutils.generators.beanmatcher.dependencies.BeanPropertyMatcher;
import io.github.marmer.testutils.generators.beanmatcher.processing.BeanProperty;
import io.github.marmer.testutils.generators.beanmatcher.processing.BeanPropertyExtractor;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

import javax.annotation.Generated;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * The Class JavaPoetHasPropertyMatcherClassGenerator.
 *
 * @author  marmer
 * @since   17.06.2017
 */
public class JavaPoetHasPropertyMatcherClassGenerator implements HasPropertyMatcherClassGenerator {
    private static final String NO_PACKAGE = "";
    private static final String PARAMETER_NAME_ITEM = "item";
	private static final String PARAMETER_NAME_DESCRIPTION = "description";
	private static final String FACTORY_METHOD_PREFIX = "is";
	private static final String INNER_MATCHER_FIELD_NAME = "beanPropertyMatcher";
    private final BeanPropertyExtractor propertyExtractor;
	private final Path outputDir;
    private final MatcherNamingStrategy matcherNamingStrategy;

	/**
	 * Creates a new Instance.
	 * @param  propertyExtractor  the property extractor
	 * @param  outputDir          the output dir
     * @param matcherNamingStrategy Strategy of how to name generated classes and packages.
	 */
	public JavaPoetHasPropertyMatcherClassGenerator(final BeanPropertyExtractor propertyExtractor,
                                                    final Path outputDir, final MatcherNamingStrategy matcherNamingStrategy) {
		this.propertyExtractor = propertyExtractor;
		this.outputDir = outputDir;
        this.matcherNamingStrategy = matcherNamingStrategy;
	}

	@Override
	public Path generateMatcherFor(final Class<?> type) throws MatcherGenerationException {
		final JavaFile javaFile = prepareJavaFile(type);
		try {
			javaFile.writeTo(outputDir);
		} catch (final IOException e) {
			throw new MatcherGenerationException("Error on writing Matcher for " + type);
		}
		return outputDir.resolve(javaFile.toJavaFileObject().getName());
	}

	private JavaFile prepareJavaFile(final Class<?> type) {
		return JavaFile.builder(packageNameFor(type),
				generatedTypeFor(type)).indent("\t")
				.skipJavaLangImports(true)
			.build();
	}

    private String packageNameFor(final Class<?> type) {
        return matcherNamingStrategy.packageFor(type).orElse(NO_PACKAGE);
    }

	private String matcherNameFor(final Class<?> type) {
		return matcherNamingStrategy.typeNameFor(type).orElseThrow(() -> new MatcherGenerationRuntimeException("Error on type name generation for the metcher for " + type));
	}

	private TypeSpec generatedTypeFor(final Class<?> type) {
		return TypeSpec.classBuilder(matcherNameFor(type))
				.addModifiers(Modifier.PUBLIC)
				.superclass(parameterizedTypesafeMatchertype(type))
				.addField(innerMatcherField(type))
				.addMethod(constructor(type))
				.addAnnotations(generatedAnnotations(type))
				.addMethods(propertyMethods(type))
				.addMethods(typesafeMatcherMethods(type))
				.addMethod(factoryMethod(type)).build();
	}

	private MethodSpec factoryMethod(final Class<?> type) {
		return MethodSpec.methodBuilder(FACTORY_METHOD_PREFIX + type.getSimpleName())
				.addStatement("return new $L()",
						matcherNameFor(type)).returns(classNameOfGeneratedTypeFor(type))
				.addModifiers(Modifier.STATIC, Modifier.PUBLIC).build();
	}

	private FieldSpec innerMatcherField(final Class<?> type) {
		return FieldSpec.builder(ParameterizedTypeName.get(BeanPropertyMatcher.class,
					type), INNER_MATCHER_FIELD_NAME, Modifier.PRIVATE, Modifier.FINAL).build();
	}

	private Iterable<MethodSpec> typesafeMatcherMethods(final Class<?> type) {
		return Arrays.asList(describeToMethod(), matchesSafelyMathod(type), describeMismatchSafelyMethod(type));
	}

	private MethodSpec describeToMethod() {
		final String parameterName = PARAMETER_NAME_DESCRIPTION;
		return MethodSpec.methodBuilder("describeTo")
				.addAnnotation(Override.class)
				.addParameter(Description.class, parameterName, Modifier.FINAL)
				.addStatement("$L.describeTo($L)", INNER_MATCHER_FIELD_NAME, parameterName)
				.addModifiers(Modifier.PUBLIC).build();
	}

	private MethodSpec matchesSafelyMathod(final Class<?> type) {
		final String parameterItem = PARAMETER_NAME_ITEM;
		return MethodSpec.methodBuilder("matchesSafely")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PROTECTED)
				.returns(Boolean.TYPE)
				.addParameter(type, parameterItem, Modifier.FINAL)
				.addStatement("return $L.matches($L)", INNER_MATCHER_FIELD_NAME, parameterItem).build();
	}

	private MethodSpec describeMismatchSafelyMethod(final Class<?> type) {
		final String parameterName = PARAMETER_NAME_ITEM;
		final String parameterNameDescription = PARAMETER_NAME_DESCRIPTION;
		return MethodSpec.methodBuilder("describeMismatchSafely")
				.addAnnotation(Override.class)
				.addParameter(type, parameterName, Modifier.FINAL)
				.addStatement("$L.describeMismatch($L, $L)", INNER_MATCHER_FIELD_NAME, parameterName, parameterNameDescription)
				.addParameter(Description.class,
						parameterNameDescription, Modifier.FINAL)
				.addModifiers(Modifier.PROTECTED).build();
	}

	private List<MethodSpec> propertyMethods(final Class<?> type) {
		return propertyExtractor.getPropertiesOf(type).stream().flatMap(property -> {
				if (Matcher.class.equals(property.getType())) {
					return Stream.of(propertyMatcherMethodFor(property, type));
				} else {
					return Stream.of(propertyMatcherMethodFor(property, type),
							propertyMethodFor(property, type));
				}
			}).collect(Collectors.toList());
	}

	private MethodSpec propertyMatcherMethodFor(final BeanProperty property, final Class<?> type) {
		return MethodSpec.methodBuilder(methodNameToGenerateFor(property.getName())).returns(
				classNameOfGeneratedTypeFor(type))
				.addModifiers(Modifier.PUBLIC)
				.addParameter(parameterizedMatchertype(), "matcher", Modifier.FINAL)
				.addStatement("$L.with($S, matcher)", INNER_MATCHER_FIELD_NAME, property.getName())
				.addStatement(
				"return this")
			.build();
	}

	private MethodSpec propertyMethodFor(final BeanProperty property, final Class<?> type) {
		return MethodSpec.methodBuilder(methodNameToGenerateFor(property.getName())).returns(
				classNameOfGeneratedTypeFor(type))
				.addModifiers(Modifier.PUBLIC)
				.addParameter(property.getType(), "value", Modifier.FINAL)
				.addStatement("$L.with($S, $T.equalTo(value))", INNER_MATCHER_FIELD_NAME, property.getName(), Matchers.class)
				.addStatement("return this")
			.build();
	}

	private ParameterizedTypeName parameterizedTypesafeMatchertype(final Class<?> type) {
		return ParameterizedTypeName.get(TypeSafeMatcher.class,
				type);
	}

	private ParameterizedTypeName parameterizedMatchertype() {
		return ParameterizedTypeName.get(ClassName.get(Matcher.class),
				WildcardTypeName.subtypeOf(TypeName.OBJECT));
	}

	private String methodNameToGenerateFor(final String propertyName) {
		return "with" + StringUtils.capitalize(propertyName);
	}

	private ClassName classNameOfGeneratedTypeFor(final Class<?> type) {
		return ClassName.get(packageNameFor(type), matcherNameFor(type));
	}

	private List<AnnotationSpec> generatedAnnotations(final Class<?> type) {
		final String annotationMemberName = "value";
		return Arrays.asList(AnnotationSpec.builder(Generated.class)
						.addMember(annotationMemberName, "$S",
					getClass().getName())
				.build(),
				AnnotationSpec.builder(BasedOn.class)
						.addMember(annotationMemberName, "$T.class", type)
					.build());
	}

	private MethodSpec constructor(final Class<?> type) {
		return MethodSpec.constructorBuilder()
				.addStatement("$L = new BeanPropertyMatcher<$T>($T.class)", INNER_MATCHER_FIELD_NAME, type, type)
				.addModifiers(
				Modifier.PUBLIC)
			.build();
	}

}
