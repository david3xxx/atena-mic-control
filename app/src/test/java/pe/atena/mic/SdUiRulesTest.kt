package pe.atena.mic

import org.junit.Assert.*
import org.junit.Test

class SdUiRulesTest {
    @Test fun exactChatTitleOnly() {
        assertTrue(SdUiRules.isSdTitle("SD"))
        assertTrue(SdUiRules.isSdTitle(" sd  "))
        assertFalse(SdUiRules.isSdTitle("SD examen"))
        assertFalse(SdUiRules.isSdTitle("Mi chat SD"))
        assertFalse(SdUiRules.isSdTitle("ChatGPT"))
    }
    @Test fun pinnedSectionIsNotChatTitle() {
        assertTrue(SdUiRules.isPinnedHeading("Fijados"))
        assertTrue(SdUiRules.isPinnedHeading("Pinned"))
        assertFalse(SdUiRules.isPinnedHeading("SD"))
    }
    @Test fun sidebarSpanishAndEnglish() {
        assertTrue(SdUiRules.isNavigationButton("Abrir barra lateral"))
        assertTrue(SdUiRules.isNavigationButton("Open sidebar"))
        assertFalse(SdUiRules.isNavigationButton("Eliminar conversación"))
    }
    @Test fun voiceButtonAvoidsDictation() {
        assertTrue(SdUiRules.isStartVoiceButton("Iniciar conversación de voz"))
        assertTrue(SdUiRules.isStartVoiceButton("Start voice chat"))
        assertTrue(SdUiRules.isStartVoiceButton("Voice"))
        assertFalse(SdUiRules.isStartVoiceButton("Dictado por voz"))
        assertFalse(SdUiRules.isStartVoiceButton("Micrófono"))
    }
    @Test fun activeVoiceIsDifferentFromStartButton() {
        assertTrue(SdUiRules.isActiveVoiceIndicator("End voice"))
        assertTrue(SdUiRules.isActiveVoiceIndicator("Salir del modo de voz"))
        assertFalse(SdUiRules.isActiveVoiceIndicator("Iniciar voz"))
    }
    @org.junit.Test fun chatComposerRules() {
        org.junit.Assert.assertTrue(SdUiRules.isChatComposer("Preguntar a ChatGPT"))
        org.junit.Assert.assertTrue(SdUiRules.isChatComposer("Ask ChatGPT"))
        org.junit.Assert.assertFalse(SdUiRules.isChatComposer("Una respuesta que habla sobre ChatGPT"))
    }
}
