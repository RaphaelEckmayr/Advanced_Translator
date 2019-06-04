package com.ibm.watson.developer_cloud.android.myapplication;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.android.library.camera.CameraHelper;
import com.ibm.watson.developer_cloud.android.library.camera.GalleryHelper;
import com.ibm.watson.developer_cloud.language_translator.v3.LanguageTranslator;
import com.ibm.watson.developer_cloud.language_translator.v3.model.TranslateOptions;
import com.ibm.watson.developer_cloud.language_translator.v3.model.TranslationResult;
import com.ibm.watson.developer_cloud.language_translator.v3.util.Language;
import com.ibm.watson.developer_cloud.service.security.IamOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.SynthesizeOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
  private final String TAG = "MainActivity";

  private EditText input;
  private ImageButton mic;
  private Button translate;
  private ImageButton Outputplay;
  private ImageButton Inputplay;
  private TextView translatedText;
  private ImageView loadedImage;

  private SpeechToText speechService;
  private TextToSpeech textService;
  private LanguageTranslator translationService;
  private String selectedBaseLanguage = Language.ENGLISH;
  private String selectedTargetLanguage = Language.SPANISH;
  private String voiceLang = SynthesizeOptions.Voice.ES_ES_ENRIQUEVOICE;

  private StreamPlayer player = new StreamPlayer();

  private CameraHelper cameraHelper;
  private GalleryHelper galleryHelper;
  private MicrophoneHelper microphoneHelper;

  private MicrophoneInputStream capture;
  private boolean listening = false;
  private Context ctx = this;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Log.d("YEET MY MEAT", Resources.getSystem().getConfiguration().locale.getLanguage());
    Log.d("YEET MY MEAT", Locale.getDefault().getDisplayLanguage());
    Log.d("YEET MY MEAT", Locale.getDefault().getLanguage());
    Log.d("YEET MY MEAT", Locale.getDefault().getISO3Language());

    cameraHelper = new CameraHelper(this);
    galleryHelper = new GalleryHelper(this);
    microphoneHelper = new MicrophoneHelper(this);

    speechService = initSpeechToTextService();
    textService = initTextToSpeechService();
    translationService = initLanguageTranslatorService();

    final Spinner targetLanguage = findViewById(R.id.target_language);
    final Spinner baseLanguage = findViewById(R.id.base_language);
    input = findViewById(R.id.input);
    mic = findViewById(R.id.mic);
    translate = findViewById(R.id.translate);
    Outputplay = findViewById(R.id.output_play);
    Inputplay = findViewById(R.id.input_play);
    translatedText = findViewById(R.id.translated_text);
    ImageButton gallery = findViewById(R.id.gallery_button);
    ImageButton camera = null; //findViewById(R.id.camera_button);
    loadedImage = findViewById(R.id.loaded_image);

    String[] langs = getResources().getStringArray(R.array.array_languages);
      Arrays.sort(langs);
    final SpinnerAdapter targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, langs);
    final SpinnerAdapter baseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, langs);
    baseLanguage.setAdapter(baseAdapter);
    targetLanguage.setAdapter(targetAdapter);


    baseLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
          selectedBaseLanguage = translateLanguage(baseAdapter.getItem(i).toString());
          translate.setEnabled(checkDuplicateSelection(baseLanguage, targetLanguage));
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {

      }
    });

    baseLanguage.setSelection(((ArrayAdapter) baseAdapter).getPosition(Locale.getDefault().getDisplayLanguage()));

    targetLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            selectedTargetLanguage = translateLanguage(targetAdapter.getItem(i).toString());
            translate.setEnabled(checkDuplicateSelection(baseLanguage, targetLanguage));
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    });


    input.addTextChangedListener(new EmptyTextWatcher() {
      @Override
      public void onEmpty(boolean empty) {
        if (empty) {
          translate.setEnabled(false);
        } else {
          translate.setEnabled(true);
        }
      }
    });

    mic.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!listening) {
          // Update the icon background
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mic.setBackgroundColor(Color.GREEN);
            }
          });
          capture = microphoneHelper.getInputStream(true);
          new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                speechService.recognizeUsingWebSocket(getRecognizeOptions(capture),
                    new MicrophoneRecognizeDelegate());
              } catch (Exception e) {
                showError(e);
              }
            }
          }).start();

          listening = true;
        } else {
          // Update the icon background
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mic.setBackgroundColor(Color.LTGRAY);
            }
          });
          microphoneHelper.closeInputStream();
          listening = false;
        }
      }
    });

    translate.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        new TranslationTask().execute(input.getText().toString());
      }
    });

    translatedText.addTextChangedListener(new EmptyTextWatcher() {
      @Override
      public void onEmpty(boolean empty) {
        if (empty) {
          Outputplay.setEnabled(false);
        } else {
          Outputplay.setEnabled(true);
        }
      }
    });

    input.addTextChangedListener(new EmptyTextWatcher() {
      @Override
      public void onEmpty(boolean empty) {
        if (empty) {
          Inputplay.setEnabled(false);
        } else {
          Inputplay.setEnabled(true);
        }
      }});

    Inputplay.setEnabled(false);
    Outputplay.setEnabled(false);

    Outputplay.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
          setVoiceLang(selectedTargetLanguage);
          if(voiceLang.equals(""))
          {
              Toast.makeText(ctx, "Voice language not supported", Toast.LENGTH_LONG).show();
          }
          else
          {
              new SynthesisTask().execute(translatedText.getText().toString());
          }
      }
    });

    Inputplay.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
          setVoiceLang(selectedBaseLanguage);
          Log.d("Yeet", voiceLang+ selectedBaseLanguage);
          if(voiceLang.equals(""))
          {
              Toast.makeText(ctx, "Voice language not supported", Toast.LENGTH_LONG).show();
          }
          else
          {
              new SynthesisTask().execute(input.getText().toString());
          }
      }
    });

    gallery.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        galleryHelper.dispatchGalleryIntent();
      }
    });

    /*camera.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cameraHelper.dispatchTakePictureIntent();
      }
    });*/


  }

  private String translateLanguage(String fullName)
  {
      switch(fullName.toUpperCase())
      {
          case "ENGLISH":
              return Language.ENGLISH;

          case "ARABIC":
              return Language.ARABIC;

          case "CHINESE":
              return Language.CHINESE;

          case "CZECH":
              return Language.CZECH;

          case "DUTCH":
              return Language.DUTCH;

          case "FRENCH":
              return Language.FRENCH;

          case "GERMAN":
              return Language.GERMAN;

          case "ITALIAN":
             return Language.ITALIAN;

          case "JAPANESE":
              return Language.JAPANESE;

          case "KOREAN":
              return Language.KOREAN;

          case "PORTUGUESE":
              return Language.PORTUGUESE;

          case "SPANISH":
              return Language.SPANISH;
      }
      return null;
  }

  private void setVoiceLang(String lang)
  {
      switch (lang.toLowerCase())
      {
          case "ENGLISH":
              voiceLang = SynthesizeOptions.Voice.EN_US_LISAVOICE;
              break;

          case "FRENCH":
              voiceLang = SynthesizeOptions.Voice.FR_FR_RENEEVOICE;
              break;

          case "GERMAN":
              voiceLang = SynthesizeOptions.Voice.DE_DE_DIETERVOICE;
              break;

          case "ITALIAN":
              voiceLang = SynthesizeOptions.Voice.IT_IT_FRANCESCAVOICE;
              break;

          case "JAPANESE":
              voiceLang = SynthesizeOptions.Voice.JA_JP_EMIVOICE;
              break;

          case "PORTUGUESE":
              voiceLang = SynthesizeOptions.Voice.PT_BR_ISABELAVOICE;
              break;

          case "SPANISH":
              voiceLang = SynthesizeOptions.Voice.ES_ES_LAURAVOICE;
              break;

          default:
              voiceLang = "";
              break;
      }
  }

  private boolean checkDuplicateSelection(Spinner baseLanguage, Spinner targetLanguage) {
      return !((String) baseLanguage.getSelectedItem()).equals((String) targetLanguage.getSelectedItem());
  }


  private void showTranslation(final String translation) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        translatedText.setText(translation);
      }
    });
  }

  private void showError(final Exception e) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        e.printStackTrace();
        // Update the icon background
        mic.setBackgroundColor(Color.LTGRAY);
      }
    });
  }

  private void showMicText(final String text) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        input.setText(text);
      }
    });
  }

  private void enableMicButton() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mic.setEnabled(true);
      }
    });
  }

  private SpeechToText initSpeechToTextService() {
    IamOptions options = new IamOptions.Builder()
            .apiKey(getString(R.string.speech_text_apikey))
            .build();
    SpeechToText service = new SpeechToText(options);
    service.setEndPoint(getString(R.string.speech_text_url));
    return service;
  }

  private TextToSpeech initTextToSpeechService() {
    IamOptions options = new IamOptions.Builder()
            .apiKey(getString(R.string.text_speech_apikey))
            .build();
    TextToSpeech service = new TextToSpeech(options);
    service.setEndPoint(getString(R.string.text_speech_url));
    return service;
  }

  private LanguageTranslator initLanguageTranslatorService() {
    IamOptions options = new IamOptions.Builder()
            .apiKey(getString(R.string.language_translator_apikey))
            .build();
    LanguageTranslator service = new LanguageTranslator("2018-05-01", options);
    service.setEndPoint(getString(R.string.language_translator_url));
    return service;
  }

  private RecognizeOptions getRecognizeOptions(InputStream captureStream) {
    return new RecognizeOptions.Builder()
            .audio(captureStream)
            .contentType(ContentType.OPUS.toString())
            .model("en-US_BroadbandModel")
            .interimResults(true)
            .inactivityTimeout(2000)
            .build();
  }

  private abstract class EmptyTextWatcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    // assumes text is initially empty
    private boolean isEmpty = true;

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
      if (s.length() == 0) {
        isEmpty = true;
        onEmpty(true);
      } else if (isEmpty) {
        isEmpty = false;
        onEmpty(false);
      }
    }

    @Override
    public void afterTextChanged(Editable s) {}

    public abstract void onEmpty(boolean empty);
  }

  private class MicrophoneRecognizeDelegate extends BaseRecognizeCallback {
    @Override
    public void onTranscription(SpeechRecognitionResults speechResults) {
      System.out.println(speechResults);
      if (speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
        String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
        showMicText(text);
      }
    }

    @Override
    public void onError(Exception e) {
      try {
        capture.close();
      } catch (IOException e1) {
        e1.printStackTrace();
      }
      showError(e);
      enableMicButton();
    }

    @Override
    public void onDisconnected() {
      enableMicButton();
    }
  }

  private class TranslationTask extends AsyncTask<String, Void, String> {

    @Override
    protected String doInBackground(String... params) {
      TranslateOptions translateOptions = new TranslateOptions.Builder()
              .addText(params[0])
              .source(selectedBaseLanguage)
              .target(selectedTargetLanguage)
              .build();
      TranslationResult result = translationService.translate(translateOptions).execute();
      String firstTranslation = result.getTranslations().get(0).getTranslationOutput();
      showTranslation(firstTranslation);
      return "Did translate";
    }
  }

  private class SynthesisTask extends AsyncTask<String, Void, String> {
    @Override
    protected String doInBackground(String... params) {
      SynthesizeOptions synthesizeOptions = new SynthesizeOptions.Builder()
              .text(params[0])
              .voice(voiceLang)
              .accept(SynthesizeOptions.Accept.AUDIO_WAV)
              .build();
      player.playStream(textService.synthesize(synthesizeOptions).execute());
      return "Did synthesize";
    }
  }

  /**
   * On request permissions result.
   *
   * @param requestCode the request code
   * @param permissions the permissions
   * @param grantResults the grant results
   */
  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions,
                                         int[] grantResults) {
    switch (requestCode) {
      case CameraHelper.REQUEST_PERMISSION: {
        // permission granted
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          cameraHelper.dispatchTakePictureIntent();
        }
      }
      case MicrophoneHelper.REQUEST_PERMISSION: {
        if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
        }
      }
    }
  }

  /**
   * On activity result.
   *
   * @param requestCode the request code
   * @param resultCode the result code
   * @param data the data
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CameraHelper.REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
      loadedImage.setImageBitmap(cameraHelper.getBitmap(resultCode));
    }

    if (requestCode == GalleryHelper.PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
      loadedImage.setImageBitmap(galleryHelper.getBitmap(resultCode, data));
    }
  }
}
