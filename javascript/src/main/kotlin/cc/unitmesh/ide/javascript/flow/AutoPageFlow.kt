package cc.unitmesh.ide.javascript.flow

import cc.unitmesh.devti.bridge.archview.model.UiComponent
import cc.unitmesh.devti.flow.TaskFlow
import cc.unitmesh.devti.gui.chat.NormalChatCodingPanel
import cc.unitmesh.devti.llms.LLMProvider
import cc.unitmesh.devti.template.GENIUS_PAGE
import cc.unitmesh.devti.template.TemplateRender
import cc.unitmesh.ide.javascript.flow.model.AutoPageContext
import kotlinx.coroutines.runBlocking

class AutoPageFlow(val context: AutoPageContext, val panel: NormalChatCodingPanel, val llm: LLMProvider) :
    TaskFlow<String> {
    override fun clarify(): String {
        val stepOnePrompt = generateStepOnePrompt(context)

        panel.addMessage(stepOnePrompt, true, stepOnePrompt)

        return runBlocking {
            val prompt = llm.stream(stepOnePrompt, "")
            return@runBlocking panel.updateMessage(prompt)
        }
    }

    private fun generateStepOnePrompt(context: AutoPageContext): String {
        val templateRender = TemplateRender(GENIUS_PAGE)
        val template = templateRender.getTemplate("page-gen-clarify.vm")

        templateRender.context = context

        val prompter = templateRender.renderTemplate(template)
        return prompter
    }


    override fun design(context: Any): List<String> {
        val componentList = context as List<UiComponent>
        val stepTwoPrompt = generateStepTwoPrompt(componentList)

        panel.addMessage(stepTwoPrompt, true, stepTwoPrompt)

        return runBlocking {
            val prompt = llm.stream(stepTwoPrompt, "")
            return@runBlocking panel.updateMessage(prompt)
        }.let { listOf(it) }
    }

    private fun generateStepTwoPrompt(selectedComponents: List<UiComponent>): String {
        val templateRender = TemplateRender(GENIUS_PAGE)
        val template = templateRender.getTemplate("page-gen-design.vm")

        context.pages = selectedComponents.map { it.format() }
        templateRender.context = context

        val prompter = templateRender.renderTemplate(template)
        return prompter
    }
}