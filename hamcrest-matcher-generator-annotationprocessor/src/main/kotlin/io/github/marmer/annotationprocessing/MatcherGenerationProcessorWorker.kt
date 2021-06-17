package io.github.marmer.annotationprocessing

import java.time.LocalDateTime
import javax.annotation.processing.Generated
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

class MatcherGenerationProcessorWorker(
    private val timeProvider: () -> LocalDateTime,
    private val processingEnv: ProcessingEnvironment,
    private val generatorName: String
) {
    fun getSupportedSourceVersion() = SourceVersion.latestSupported()

    fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) {
            return false
        }
        processingEnv.logNote("Annotation processor for hamcrest matcher generation started")
        return if (annotations.contains<MatcherConfiguration>()) {
            roundEnv.getElementsAnnotatedWith<MatcherConfiguration>()
                .forEach { generateMatcherBy(it, roundEnv) }
            true
        } else {
            false
        }
    }

    private fun generateMatcherBy(generationConfiguration: Element, roundEnv: RoundEnvironment) {
        getAllTypeElementsFor(generationConfiguration)
            .forEach {
                if (it.isSelfGenerated()) {
                    printSkipNoteBecauseOfSelfGenerationFor(it)
                } else {
                    MatcherGenerator(
                        processingEnv,
                        it,
                        timeProvider,
                        generatorName,
                        roundEnv.getTypesWithAsserters(),
                        listOf(generationConfiguration)
                    ).generate()
                }
            }
    }

    private fun RoundEnvironment.getTypesWithAsserters(): Collection<TypeElement> =
        getElementsAnnotatedWith<MatcherConfiguration>()
            .flatMap { getAllTypeElementsFor(it) }
            .flatMap { expandToNestedTypes(it) }
            .distinct()

    private fun expandToNestedTypes(typeElement: TypeElement): List<TypeElement> =
        listOf(typeElement) +
                typeElement.enclosedElements
                    .filterIsInstance(TypeElement::class.java)
                    .filter(TypeElement::isPublic)
                    .flatMap { expandToNestedTypes(it) }

    private fun getAllTypeElementsFor(configurationType: Element): List<TypeElement> {
        return configurationType
            .getAnnotation(MatcherConfiguration::class.java)
            .value
            .distinct()
            .flatMap { getAllTypeElementsFor(it, configurationType) }
            .distinct()
    }

    private fun getAllTypeElementsFor(
        currentQualifiedTypeOrPackageName: String,
        configurationType: Element
    ): List<TypeElement> {
        val typeElementsForName = processingEnv.elementUtils
            .getAllPackageElements(currentQualifiedTypeOrPackageName)
            .flatMap { it.enclosedElements }
            .map { it as TypeElement }
            .plus(
                getHighestNestingType(
                    processingEnv
                        .elementUtils
                        .getTypeElement(currentQualifiedTypeOrPackageName)
                )
            ).filterNotNull()

        if (typeElementsForName.isEmpty())
            printSkipWarningBecauseOfNotExistingTypeConfigured(configurationType, currentQualifiedTypeOrPackageName)

        return typeElementsForName
    }

    private fun getHighestNestingType(typeElement: TypeElement?): TypeElement? =
        if (typeElement != null && typeElement.enclosingElement is TypeElement)
            getHighestNestingType(typeElement.enclosingElement as TypeElement)
        else
            typeElement

    private fun printSkipWarningBecauseOfNotExistingTypeConfigured(
        configurationClass: Element,
        qualifiedTypeOrPackageName: String
    ) {
        configurationClass.annotationMirrors
            .filter { it.isTypeOf<MatcherConfiguration>() }
            .forEach {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.MANDATORY_WARNING,
                    "Neither a type nor a type exists for '$qualifiedTypeOrPackageName'",
                    configurationClass,
                    it,
                    it.getAnnotationValueForField("value")
                )
            }
    }

    private fun printSkipNoteBecauseOfSelfGenerationFor(baseType: TypeElement) {
        baseType.annotationMirrors
            .filter { it.isTypeOf<Generated>() }
            .forEach {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.NOTE,
                    "Generation skipped for: '${baseType.qualifiedName}' because is is already generated by this processor",
                    baseType,
                    it,
                    it.getAnnotationValueForField("value")
                )
            }
    }

    private fun Element.isSelfGenerated(): Boolean =
        getAnnotation(Generated::class.java)
            .let {
                it != null && it.value.any { value -> value == generatorName }
            }

    private inline fun <reified T : Annotation> AnnotationMirror.isTypeOf() =
        annotationType.asElement().toString() == T::class.qualifiedName

    private fun AnnotationMirror.getAnnotationValueForField(fieldName: String) =
        elementValues.get(elementValues.keys.first { it.simpleName.contentEquals(fieldName) })

    private inline fun <reified T : Annotation> RoundEnvironment.getElementsAnnotatedWith() =
        getElementsAnnotatedWith(T::class.java)

    private inline fun <reified T> Set<TypeElement>.contains() =
        this.find { T::class.qualifiedName == it.qualifiedName.toString() } != null
}

internal fun ProcessingEnvironment.logNote(message: String, element: Element? = null) =
    messager.printMessage(Diagnostic.Kind.NOTE, message, element)

internal val Element.isPrivate: Boolean
    get() = modifiers.contains(Modifier.PRIVATE)

internal val Element.isPublic: Boolean
    get() = modifiers.contains(Modifier.PUBLIC)
