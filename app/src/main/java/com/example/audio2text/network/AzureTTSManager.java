package com.example.audio2text.network;

import android.content.Context;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import java.io.File;
import java.io.FileOutputStream;
import com.example.audio2text.BuildConfig;

public class AzureTTSManager {
    private SpeechConfig speechConfig;
    String key = BuildConfig.AZURE_KEY;
    String region = BuildConfig.AZURE_REGION;

    public AzureTTSManager() {
        speechConfig = SpeechConfig.fromSubscription(key, region);
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