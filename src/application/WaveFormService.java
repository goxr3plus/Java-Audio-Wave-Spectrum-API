package application;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import ws.schild.jave.AudioAttributes;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.EncoderProgressListener;
import ws.schild.jave.EncodingAttributes;
import ws.schild.jave.MultimediaInfo;
import ws.schild.jave.MultimediaObject;

public class WaveFormService extends Service<Boolean> {
	
	private static final double WAVEFORM_HEIGHT_COEFFICIENT = 2.4; // This fits the waveform to the swing node height
	private static final CopyOption[] options = new CopyOption[]{ COPY_ATTRIBUTES , REPLACE_EXISTING };
	private float[] resultingWaveform;
	private int[] wavAmplitudes;
	private String fileAbsolutePath;
	private final WaveVisualization waveVisualization;
	private final Random random = new Random();
	private File temp1;
	private File temp2;
	private Encoder encoder;
	private ConvertProgressListener listener = new ConvertProgressListener();
	
	/**
	 * Constructor.
	 */
	public WaveFormService(WaveVisualization waveVisualization) {
		this.waveVisualization = waveVisualization;
		
		setOnSucceeded(s -> done());
		setOnFailed(f -> failure());
		setOnCancelled(c -> failure());
	}
	
	/**
	 * Start the external Service Thread.
	 *
	 * 
	 */
	public void startService(String fileAbsolutePath) {
		this.fileAbsolutePath = fileAbsolutePath;
		this.resultingWaveform = null;
		this.wavAmplitudes = null;
		
		//Go
		restart();
	}
	
	/**
	 * Done.
	 */
	// Work done
	public void done() {
		waveVisualization.startPainterService();
		//	waveVisualization.setWaveData(resultingWaveform)
		//	waveVisualization.paintWaveForm()
		deleteTemporaryFiles();
	}
	
	private void failure() {
		deleteTemporaryFiles();
	}
	
	/**
	 * Delete temporary files
	 */
	private void deleteTemporaryFiles() {
		temp1.delete();
		temp2.delete();
	}
	
	@Override
	protected Task<Boolean> createTask() {
		return new Task<Boolean>() {
			
			@Override
			protected Boolean call() throws Exception {
				
				//Try to get the resultingWaveForm
				try {
					
					String fileFormat = "mp3";
					//		                if ("wav".equals(fileFormat))
					//		                    resultingWaveform = processFromWavFile();
					//		                else if ("mp3".equals(fileFormat) || "m4a".equals(fileFormat))
					resultingWaveform = processFromNoWavFile(fileFormat);
					
					System.out.println("Service done successfully");
				} catch (Exception ex) {
					ex.printStackTrace();
					return false;
				}
				
				return true;
				
			}
			
			//			private float[] processFromWavFile() throws IOException , UnsupportedAudioFileException {
			//				File trackFile = new File(trackToAnalyze.getFileFolder(), trackToAnalyze.getFileName());
			//				return processAmplitudes(getWavAmplitudes(trackFile));
			//			}
			
			/**
			 * Try to process a Non Wav File
			 * 
			 * @param fileFormat
			 * @return
			 * @throws IOException
			 * @throws UnsupportedAudioFileException
			 * @throws EncoderException
			 */
			private float[] processFromNoWavFile(String fileFormat) throws IOException , UnsupportedAudioFileException , EncoderException {
				int randomN = random.nextInt(99999);
				
				//Create temporary files
				File temporalDecodedFile = File.createTempFile("decoded_" + randomN, ".wav");
				File temporalCopiedFile = File.createTempFile("original_" + randomN, "." + fileFormat);
				temp1 = temporalDecodedFile;
				temp2 = temporalCopiedFile;
				
				//Delete temporary Files on exit
				temporalDecodedFile.deleteOnExit();
				temporalCopiedFile.deleteOnExit();
				
				//Create a temporary path
				Files.copy(new File(fileAbsolutePath).toPath(), temporalCopiedFile.toPath(), options);
				
				//Transcode to .wav
				transcodeToWav(temporalCopiedFile, temporalDecodedFile);
				
				//Avoid creating amplitudes again for the same file
				if (wavAmplitudes == null)
					wavAmplitudes = getWavAmplitudes(temporalDecodedFile);
				
				//Delete temporary files
				temporalDecodedFile.delete();
				temporalCopiedFile.delete();
				
				return processAmplitudes(wavAmplitudes);
			}
			
			/**
			 * Process the amplitudes
			 * 
			 * @param sourcePcmData
			 * @return An array with amplitudes
			 */
			private float[] processAmplitudes(int[] sourcePcmData) {
				System.out.println("Processing amplitudes");
				int width = waveVisualization.width;    // the width of the resulting waveform panel
				float[] waveData = new float[width];
				int samplesPerPixel = sourcePcmData.length / width;
				
				for (int w = 0; w < width; w++) {
					float nValue = 0.0f;
					
					for (int s = 0; s < samplesPerPixel; s++) {
						nValue += ( Math.abs(sourcePcmData[w * samplesPerPixel + s]) / 65536.0f );
					}
					nValue /= samplesPerPixel;
					waveData[w] = nValue;
				}
				
				System.out.println("Finished Processing amplitudes");
				return waveData;
			}
			
			/**
			 * Get Wav Amplitudes
			 * 
			 * @param file
			 * @return
			 * @throws UnsupportedAudioFileException
			 * @throws IOException
			 */
			private int[] getWavAmplitudes(File file) throws UnsupportedAudioFileException , IOException {
				System.out.println("Calculting amplitudes");
				int[] amplitudes = null;
				double fixer = WAVEFORM_HEIGHT_COEFFICIENT;//4 / 100.00 * waveVisualization.height;
				System.out.println("fixer :" + fixer);
				try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
					AudioFormat baseFormat = input.getFormat();
					
					Encoding encoding = AudioFormat.Encoding.PCM_UNSIGNED;
					float sampleRate = baseFormat.getSampleRate();
					int numChannels = baseFormat.getChannels();
					
					AudioFormat decodedFormat = new AudioFormat(encoding, sampleRate, 16, numChannels, numChannels * 2, sampleRate, false);
					int available = input.available();
					amplitudes = new int[available];
					System.out.println("After  decodedFormat");
					
					try (AudioInputStream pcmDecodedInput = AudioSystem.getAudioInputStream(decodedFormat, input)) {				
						byte[] buffer = new byte[available];
						System.out.println("BEfore read");
						pcmDecodedInput.read(buffer, 0, available);
						System.out.println("After read");
						for (int i = 0; i < available - 1; i += 2) {
							//System.out.println("Inside Loop");
							amplitudes[i] = ( ( buffer[i + 1] << 8 ) | buffer[i] & 0xff ) << 16;
							amplitudes[i] /= 32767;
							amplitudes[i] *= fixer;
							
						}
					}catch(Exception ex) {
						ex.printStackTrace();
					}
				}catch(Exception ex) {
					ex.printStackTrace();
				}
				System.out.println("Finished Calculting amplitudes");
				return amplitudes;
			}
			
			/**
			 * Transcode to Wav
			 * 
			 * @param sourceFile
			 * @param destinationFile
			 * @throws EncoderException
			 */
			private void transcodeToWav(File sourceFile , File destinationFile) throws EncoderException {
				//Attributes atters = DefaultAttributes.WAV_PCM_S16LE_STEREO_44KHZ.getAttributes()
				try {
					AudioAttributes audio = new AudioAttributes();
					audio.setCodec("pcm_s16le");
					audio.setChannels(2);
					audio.setSamplingRate(44100);
					EncodingAttributes attributes = new EncodingAttributes();
					attributes.setFormat("wav");
					attributes.setAudioAttributes(audio);
					//Initialize if encoder is null
					if (encoder == null)
						encoder = new Encoder();
					//Go do it
					encoder.encode(new MultimediaObject(sourceFile), destinationFile, attributes, listener);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};
	}
	
	public class ConvertProgressListener implements EncoderProgressListener {
		int current = 1;
		
		public ConvertProgressListener() {
		}
		
		public void message(String m) {
			//      if ((ConverterFrame.this.inputfiles.length > 1) && 
			//        (this.current < ConverterFrame.this.inputfiles.length)) {
			//        ConverterFrame.this.encodingMessageLabel.setText(this.current + "/" + ConverterFrame.this.inputfiles.length);
			//      }
		}
		
		public void progress(int p) {
			
			double progress = p / 1000.00;
			System.out.println(progress);
			
			//Platform.runLater(() -> convertProgress.set(progress));
			//      ConverterFrame.this.encodingProgressLabel.setText(progress + "%");
			//      if (p >= 1000) {
			//        if (ConverterFrame.this.inputfiles.length > 1)
			//        {
			//          this.current += 1;
			//          if (this.current > ConverterFrame.this.inputfiles.length)
			//          {
			//            ConverterFrame.this.encodingMessageLabel.setText("Encoding Complete!");
			//            ConverterFrame.this.convertButton.setEnabled(true);
			//          }
			//        }
			//        else if (p == 1001)
			//        {
			//          ConverterFrame.this.encodingMessageLabel.setText("Encoding Failed!");
			//          ConverterFrame.this.convertButton.setEnabled(true);
			//        }
			//        else
			//        {
			//          ConverterFrame.this.encodingMessageLabel.setText("Encoding Complete!");
			//          ConverterFrame.this.convertButton.setEnabled(true);
			//        }
		}
		
		public void sourceInfo(MultimediaInfo m) {
		}
	}
	
	public String getFileAbsolutePath() {
		return fileAbsolutePath;
	}
	
	public void setFileAbsolutePath(String fileAbsolutePath) {
		this.fileAbsolutePath = fileAbsolutePath;
	}
	
	public int[] getWavAmplitudes() {
		return wavAmplitudes;
	}
	
}
