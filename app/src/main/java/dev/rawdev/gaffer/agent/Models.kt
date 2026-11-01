package dev.rawdev.gaffer.agent

enum class Role { USER, ASSISTANT, TOOL, ERROR, SYSTEM }

data class UiMessage(
    val id: Long,
    val role: Role,
    val title: String = "",
    val text: String = ""
)

data class PendingApproval(
    val id: String,
    val tool: String,
    val description: String
)

data class AgentState(
    val messages: List<UiMessage> = emptyList(),
    val running: Boolean = false,
    val step: Int = 0,
    val pending: PendingApproval? = null,
    val status: String = ""
)

data class ToolResult(
    val ok: Boolean,
    val content: String,
    val terminal: Boolean = false
)
