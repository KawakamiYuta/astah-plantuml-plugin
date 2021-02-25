package com.change_vision.astah.plugins.converter

import com.change_vision.jude.api.inf.AstahAPI
import com.change_vision.jude.api.inf.editor.TransactionManager
import com.change_vision.jude.api.inf.exception.BadTransactionException
import com.change_vision.jude.api.inf.model.IClass
import com.change_vision.jude.api.inf.model.IDiagram
import com.change_vision.jude.api.inf.model.ILifeline
import com.change_vision.jude.api.inf.model.IMessage
import com.change_vision.jude.api.inf.presentation.ILinkPresentation
import net.sourceforge.plantuml.SourceStringReader
import net.sourceforge.plantuml.sequencediagram.Message
import net.sourceforge.plantuml.sequencediagram.ParticipantType
import net.sourceforge.plantuml.sequencediagram.SequenceDiagram


object PlantToAstahSequenceDiagramConverter {
    private const val xSpan = 20.0
    private const val ySpan = 40.0
    private const val initY = 50.0

    private val api = AstahAPI.getAstahAPI()
    private val projectAccessor = api.projectAccessor
    private val modelEditor = projectAccessor.modelEditorFactory.basicModelEditor
    private val diagramEditor = projectAccessor.diagramEditorFactory.sequenceDiagramEditor
    fun convert(diagram: SequenceDiagram, reader: SourceStringReader, index: Int) {
        // create diagram
        val sequenceDiagram = createOrGetDiagram(index)

        // convert lifeline
        TransactionManager.beginTransaction()
        try {
            var prevX = 0.0
            val participantMap = diagram.participants().mapIndexedNotNull { i, participant ->
                val editorFactory = projectAccessor.modelEditorFactory
                val baseClass: IClass? = when (participant.type) {
                    ParticipantType.ACTOR -> editorFactory.useCaseModelEditor.createActor(
                        projectAccessor.project,
                        participant.code
                    )
                    ParticipantType.BOUNDARY -> editorFactory.basicModelEditor.createClass(
                        projectAccessor.project,
                        participant.code
                    ).also { it.addStereotype("boundary") }
                    ParticipantType.ENTITY -> editorFactory.basicModelEditor.createClass(
                        projectAccessor.project,
                        participant.code
                    ).also { it.addStereotype("entity") }
                    ParticipantType.CONTROL -> editorFactory.basicModelEditor.createClass(
                        projectAccessor.project,
                        participant.code
                    ).also { it.addStereotype("control") }
                    else -> null
                }
                if (baseClass == null) {
                    val lifeline = diagramEditor.createLifeline(participant.code, prevX)
                    prevX += lifeline.width + xSpan
                    Pair(participant, lifeline)
                } else {
                    val lifeline = diagramEditor.createLifeline("", prevX)
                    (lifeline.model as ILifeline).base = baseClass
                    prevX += lifeline.width + xSpan
                    Pair(participant, lifeline)
                }
            }.toMap()

            // convert messages
            var prevMessage: ILinkPresentation? = null // TODO
            diagram.events().forEachIndexed { i, event ->
                if (event is Message) {
                    val label = when {
                        event.label.isWhite -> event.messageNumber + "message()"
                        event.label.toString().isBlank() -> event.messageNumber + "message()"
                        else -> event.label.toString().replace("[\\[\\]]".toRegex(), "")
                    }
                    val message =
                        when {
                            event.arrowConfiguration.isDotted && prevMessage != null ->
                                diagramEditor.createReturnMessage(label, prevMessage)
                            event.isCreate -> diagramEditor.createCreateMessage(
                                label,
                                participantMap[event.participant1],
                                participantMap[event.participant2],
                                initY + ySpan * (i + 1)
                            )
                            else -> diagramEditor.createMessage(
                                label,
                                participantMap[event.participant1],
                                participantMap[event.participant2],
                                initY + ySpan * (i + 1)
                            )
                        }
                    when {
                        event.arrowConfiguration.isAsync -> (message.model as IMessage).isAsynchronous = true
                    }
                    prevMessage = message
                }
            }
        } catch (e: Exception) {
            TransactionManager.abortTransaction()
            return
        }
        TransactionManager.endTransaction()
        if (sequenceDiagram != null) {
            api.viewManager.diagramViewManager.open(sequenceDiagram)
        }
    }

    private fun createOrGetDiagram(index: Int): IDiagram? {
        val diagramName = "SequenceDiagram_$index"

        val foundDiagramList = projectAccessor.findElements(IDiagram::class.java, diagramName)
        return when {
            foundDiagramList.isNotEmpty() -> {
                foundDiagramList.first() as IDiagram
            }
            else -> {
                TransactionManager.beginTransaction()
                try {
                    diagramEditor.createSequenceDiagram(projectAccessor.project, diagramName)
                        .also { TransactionManager.endTransaction() }
                } catch (e: BadTransactionException) {
                    TransactionManager.abortTransaction()
                    null
                }
            }
        }
    }
}