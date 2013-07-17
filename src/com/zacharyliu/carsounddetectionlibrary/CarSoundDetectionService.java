package com.zacharyliu.carsounddetectionlibrary;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.renderscript.Sampler;

import com.zacharyliu.carsounddetectionlibrary.analyzer.DataBuffer;
import com.zacharyliu.carsounddetectionlibrary.analyzer.FeatureVector;
import com.zacharyliu.carsounddetectionlibrary.analyzer.FeatureVectorExtractor;
import com.zacharyliu.carsounddetectionlibrary.analyzer.classifiers.Classifier;
import com.zacharyliu.carsounddetectionlibrary.analyzer.classifiers.NeuralNetworkClassifier;

public class CarSoundDetectionService extends Service {
	
	private CarSoundDetectionReceiver mReceiver;
	private static final int RECORDER_SAMPLERATE = 44100;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
	
	private static final int SHORT_16BIT_CONSTANT = 32768; // 2^(16-1) for signed 16-bit short
	
	public class CarSoundDetectionBinder extends Binder {		
		public void start(CarSoundDetectionReceiver receiver) {
			mReceiver = receiver;
			analyzer.start();
		}
	}
	private CarSoundDetectionBinder mBinder = new CarSoundDetectionBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	public String getString() {
		return "Service Started";
	}
	
	private Thread analyzer = new Thread() {
		private Classifier classifier = new NeuralNetworkClassifier();
		private FeatureVectorExtractor extractor = new FeatureVectorExtractor(RECORDER_SAMPLERATE);
//		private DataBuffer<Double> buffer = new DataBuffer<Double>(100);
		short[] raw_buffer = new short[BUFFER_SIZE];
		List<Integer> buffer;
		List<List<Integer>> results = new ArrayList<List<Integer>>();
		
		private void push(List<Integer> samples) {
			List<FeatureVector> feature_vectors = extractor.push(samples);
			results.clear();
			for (FeatureVector vector : feature_vectors) {
				List<Integer> result = classifier.run(vector);
				results.add(result);
			}
		}
		
		@Override
		public void run() {
			// TODO: run FFT
			AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, BUFFER_SIZE);
			while (true) {
				recorder.read(raw_buffer, 0, BUFFER_SIZE);
				buffer = convertBuffer(raw_buffer);
				push(buffer);
				for (List<Integer> result : results) {
					mReceiver.onResult(result);
				}
			}
		}
		
		private List<Integer> convertBuffer(short[] buffer) {
			List<Integer> output = new ArrayList<Integer>(buffer.length);
			for (short i : buffer) {
				output.add(i / SHORT_16BIT_CONSTANT);
			}
			return output;
		}
	};
	
}