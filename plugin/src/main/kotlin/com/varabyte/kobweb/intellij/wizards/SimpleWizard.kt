package com.varabyte.kobweb.intellij.wizards

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * A simple wizard (i.e., multi-step dialog flow) for our plugin.
 *
 * The wizard bundled with IntelliJ is way too complicated for our needs. So this minimal one should suffice!
 *
 * To use it, a user must first subclass it, with inputs and outputs:
 * ```
 * class MyWizard(project: Project, input: Input) : SimpleWizard<Input, Output>(
 *     project,
 *     "My Title",
 *     input
 * ) {
 *     class Input(...)
 *     class Output(...)
 * }
 * ```
 * For example, maybe the input is a `PsiElement`, plus some additional configuration arguments. And the output might be
 * some class full of information collected from the user's code, having used that `PsiElement` as a starting point.
 *
 * Next, the basic design is to create one or more steps which each do some small amount of work towards the overall
 * wizard's goal. A [SimpleWizard.Data] class, which is just a basic hashmap using IntelliJ [Key] keys for type-safety,
 * will be provided for steps to fill as they each get their turn. Finally, this wizard will get a chance to pull values
 * out of the data object, creating the promised output class.
 *
 * ```
 * private val KEY_A = Key(0)
 * private val KEY_B = Key("hello")
 *
 * class MyWizard(...) : SimpleWizard {
 *     override fun createSteps(ctx: StepContext) = listOf(
 *         object : Step {
 *             ...
 *         },
 *         object : Step {
 *             ...
 *         }
 *     )
 *
 *     override fun onFinished(data: Data) = Output(
 *         data.getUserData(KEY_A)!!,
 *         data.getUserData(KEY_B)!!,
 *     )
 * }
 * ```
 *
 * Once everything is in place, then from the user's point of view, they just instantiate your wizard subclass and call
 * [show]:
 * ```
 * val result = MyWizard(project, MyWizard.Input(...)).show() ?: return
 * ```
 */
abstract class SimpleWizard<I, R>(
    val project: Project,
    @get:DialogTitle
    val title: String,
    val input: I,
    val size: Dimension = JBUI.size(400, 350)
) {
    protected class Data : UserDataHolderBase()

    class StepUtils(project: Project, disposable: Disposable) {
        val components = Components(project, disposable)
        class Components(private val project: Project, private val disposable: Disposable) {
            fun formBuilder(): FormBuilder = FormBuilder.createFormBuilder().setVerticalGap(8)

            fun tabs(init: JBTabs.() -> Unit): JBTabs {
                fun JBTabs.selectedIndex() = selectedInfo?.let { tabs.indexOf(it) } ?: 0

                return JBTabsFactory.createTabs(project).apply {
                    component.addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_RIGHT) {
                                val next = (selectedIndex() + 1) % tabCount
                                select(tabs[next], true)
                            } else if (e.keyCode == KeyEvent.VK_LEFT) {
                                val next = (selectedIndex() - 1 + tabCount) % tabCount
                                select(tabs[next], true)
                            }
                        }
                    })
                    init()
                }
            }

            fun kotlinCode(code: String = ""): EditorTextField {
                val kotlinFileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")

                return EditorTextField(
                    code,
                    project,
                    kotlinFileType,
                ).apply {
                    isViewer = true
                    border = JBUI.Borders.customLine(JBColor.border(), 1)

                    setDisposedWith(disposable)
                    @Suppress("UsePropertyAccessSyntax") // Bad suggestion, causes a compile error
                    setOneLineMode(false)
                    setFontInheritedFromLAF(false) // Use editor font

                    addSettingsProvider { editor ->
                        editor.contentComponent.border = JBUI.Borders.empty(8)
                        editor.scrollPane.apply {
                            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                        }
                    }
                }
            }

            fun rememberCheckbox(): JBCheckBox {
                return JBCheckBox("Remember this choice for later")
            }

            fun doNotShowAgainCheckbox(): JBCheckBox {
                return JBCheckBox("Do not show this again")
            }

            fun doNotAskAgainCheckbox(): JBCheckBox {
                return JBCheckBox("Do not ask this again")
            }
        }

        val validation = Validation()
        class Validation {
            /**
             * Convert an [InputValidatorEx] into a [ValidationInfo] error which can be returned by [Step.validate].
             *
             * As an error, this will stop the step from continuing until it is resolved.
             *
             * Use `with(utls.validation) { ... }` in order to access this extension method.
             */
            fun InputValidatorEx.toValidationError(textComponent: JTextComponent): ValidationInfo? {
                val input = textComponent.text.trim()
                return if (!checkInput(input)) {
                    ValidationInfo(getErrorText(input).orEmpty(), textComponent)
                } else null
            }

            /**
             * Convert an [InputValidatorEx] into a [ValidationInfo] warning which can be returned by [Step.validate].
             *
             * As a warning, this will not block the wizard step from continuing.
             *
             * Use `with(utls.validation) { ... }` in order to access this extension method.
             */
            fun InputValidatorEx.toValidationWarning(textComponent: JTextComponent): ValidationInfo? {
                return toValidationError(textComponent)?.asWarning()?.withOKEnabled()
            }
        }
    }

    protected inner class StepContext(
        val project: Project,
        val disposable: Disposable,
        val input: I,
        val data: Data,
    ) {
        val utils = StepUtils(project, disposable)
    }
    protected interface Step {
        /**
         * A function that will be tested to see if this step can be shown based on the current state of the wizard.
         *
         * Note that this method may get called on the same step MULTIPLE times over the lifetime of a wizard. For
         * example, perhaps the step should only be shown because a user selected a non-standard option on a previous
         * step.
         */
        fun shouldShow(): Boolean = true

        @get:DialogTitle
        val headerText: String

        /**
         * Produce the contents of this step.
         *
         * This will only be called once, when the wizard is first created, and before any steps are actually visited.
         *
         * If you need to, you can further tweak the UI in [onEntering].
         */
        fun produceComponent(): JComponent

        /**
         * If returned, this is the component that should take focus when a new page is entered.
         *
         * If not set, then the focus will stay on the Next / Finish button.
         *
         * Focus will only be set on forward movement, not when going back to a previous step.
         */
        val initialFocusedComponent: JComponent? get() = null

        /**
         * The current step should return a [ValidationInfo] if the page should not be able to proceed.
         */
        fun validate(): ValidationInfo? = null

        /**
         * Event when the step is about to be shown.
         *
         * This can be a useful place to tweak UI based on values set by previous steps. (Remember, [produceComponent]
         * is called before any steps are even visited.)
         *
         * This will get called only when moving forward into a step. If you press "next" and then "back", this method
         * will not be triggered. However, if you are on this step and press "back" and then "next" again, it will be
         * called a second time.
         */
        fun onEntering() { }

        /**
         * Event triggered when the user hit "next" while on the current step
         */
        fun onNext() { }
    }

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    protected abstract fun createSteps(ctx: StepContext): List<Step>

    protected abstract fun onFinished(data: Data): R

    /**
     * Show this wizard! It will be
     */
    fun show(): R? {
        val data = Data()
        val dialog = object : DialogWrapper(project) {
            val ctx = StepContext(
                project,
                disposable,
                input,
                data
            )

            private val steps = createSteps(ctx)
                .also {
                    it.forEachIndexed { index, step ->
                        cardPanel.add(
                            FormBuilder.createFormBuilder()
                                .addComponent(JBLabel("<html><h3>${step.headerText}</h3></html>"))
                                .addComponentFillVertically(step.produceComponent(), 0)
                                .panel,
                            index.toString()
                        )
                    }
                }
            private var currentStepIndex: Int = steps.indexOfFirst { it.shouldShow() }.takeIf { it >= 0 }
                ?: error { "Trying to create a wizard with no visible steps!" }
            private val history = mutableListOf<Int>()

            private val nextStepIndex: Int? get() {
                for (i in (currentStepIndex + 1)..(steps.lastIndex)) {
                    if (steps[i].shouldShow()) {
                        return i
                    }
                }
                return null
            }

            private val backAction = object : DialogWrapperAction("< Back") {
                override fun doAction(e: ActionEvent) {
                    setCurrentStep(history.removeLast(), movingForward = false)
                }
            }

            init {
                this.title = this@SimpleWizard.title
                init()
                initValidation()
            }

            override fun createActions(): Array<out Action> {
                return arrayOf(
                    cancelAction,
                    backAction,
                    okAction,
                )
            }

            override fun createCenterPanel(): JComponent {
                updateCurrentStep(movingForward = true)

                return JPanel(BorderLayout()).apply {
                    preferredSize = this@SimpleWizard.size
                    minimumSize = this@SimpleWizard.size
                    add(cardPanel, BorderLayout.CENTER)
                }
            }

            override fun doOKAction() {
                steps[currentStepIndex].onNext()
                nextStepIndex?.let { nextStepIndex ->
                    history.add(currentStepIndex)
                    setCurrentStep(nextStepIndex, movingForward = true)
                } ?: run {
                    close(OK_EXIT_CODE)
                }
            }

            override fun doValidate(): ValidationInfo? = steps[currentStepIndex].validate()

            private fun setCurrentStep(index: Int, movingForward: Boolean) {
                if (currentStepIndex != index) {
                    currentStepIndex = index
                    updateCurrentStep(movingForward)
                }
            }

            private fun updateCurrentStep(movingForward: Boolean) {
                val step = steps[currentStepIndex]
                check(step.shouldShow()) { "Trying to show a step that doesn't want to be shown!" }

                if (movingForward) step.onEntering()

                cardLayout.show(cardPanel, currentStepIndex.toString())
                val isLastVisibleStep = nextStepIndex == null

                setOKButtonText(if (isLastVisibleStep) "Finish" else "Next >")
                backAction.isEnabled = history.isNotEmpty()

                if (movingForward) {
                    step.initialFocusedComponent?.let { toFocus ->
                        SwingUtilities.invokeLater { toFocus.requestFocusInWindow() }
                    }
                }
            }
        }
        if (!dialog.showAndGet()) return null
        return onFinished(data)
    }
}