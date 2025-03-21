package cc.unitmesh.devti.provider.devins

import cc.unitmesh.devti.agent.custom.model.CustomAgentConfig
import cc.unitmesh.devti.devin.dataprovider.BuiltinCommand
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

data class CustomAgentContext(
    val config: CustomAgentConfig,
    val response: String
)

/**
 * Handle the response of the custom agent, and return the result to the user.
 * Specify for [cc.unitmesh.devti.language.DevInLanguage]
 */
interface LanguageProcessor {
    val name: String

    /**
     * For CustomAgentExecutor to execute the code
     */
    @RequiresBackgroundThread
    suspend fun execute(project: Project, context: CustomAgentContext): String

    @RequiresBackgroundThread
    suspend fun compile(project: Project, text: String): String

    @RequiresBackgroundThread
    fun transpileCommand(project: Project, psiFile: PsiFile): List<BuiltinCommand>

    companion object {
        val EP_NAME = ExtensionPointName<LanguageProcessor>("cc.unitmesh.languageProcessor")

        private fun instance(languageName: String): List<LanguageProcessor> {
            return EP_NAME.extensionList.filter { it.name == languageName }
        }

        fun devin(): LanguageProcessor? {
            return instance("DevIn").firstOrNull()
        }
    }
}