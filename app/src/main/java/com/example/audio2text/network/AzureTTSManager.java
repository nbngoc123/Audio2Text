package com.example.audio2text.network;

import android.content.Context;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import java.io.File;
import java.io.FileOutputStream;

public class AzureTTSManager {
    private SpeechConfig speechConfig;
    private static final String AZURE_KEY = "";
    private static final String AZURE_REGION = "eastasia";

    public AzureTTSManager() {
        speechConfig = SpeechConfig.fromSubscription(AZURE_KEY, AZURE_REGION);
        speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Riff24Khz16BitMonoPcm);
        speechConfig.setSpeechSynthesisVoiceName("vi-VN-NamMinhNeural");
    }

    public File synthesize(String text, String fileName, Context context) throws Exception {
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(speechConfig, null);
        SpeechSynthesisResult result = synthesizer.SpeakText(text);

        if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
            byte[] audioData = result.getAudioData();
            File audioFile = new File(context.getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                fos.write(audioData);
            }
            synthesizer.close();
            return audioFile;
        } else {
            String error = result.getProperties().getProperty(com.microsoft.cognitiveservices.speech.PropertyId.SpeechServiceResponse_JsonErrorDetails);
            synthesizer.close();
            throw new Exception("Azure TTS Error: " + error);
        }
    }
}