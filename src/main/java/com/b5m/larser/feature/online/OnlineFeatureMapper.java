package com.b5m.larser.feature.online;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.SequentialAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.apache.mahout.math.VectorWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b5m.larser.feature.RequestWritable;

import static com.b5m.HDFSHelper.*;

public class OnlineFeatureMapper extends
		Mapper<IntWritable, RequestWritable, Text, VectorWritable> {
	private static final Logger LOG = LoggerFactory
			.getLogger(OnlineFeatureMapper.class);
	private Vector alpha = null;
	private Vector beta = null;
	private Vector ACj = null;
	private Matrix A = null;

	protected void setup(Context context) throws IOException,
			InterruptedException {
		Configuration conf = context.getConfiguration();
		String offlineModel = conf
				.get("com.b5m.laser.online.feature.offline.model");
		Path offlinePath = new Path(offlineModel);
		Path alpha = new Path(offlinePath, "alpha");
		Path beta = new Path(offlinePath, "beta");
		Path A = new Path(offlinePath, "A");
		try {
			FileSystem fs = alpha.getFileSystem(conf);
			this.alpha = readVector(alpha, fs, conf);
			this.beta = readVector(beta, fs, conf);
			this.A = readMatrix(A, fs, conf);
		} catch (IOException e) {
			LOG.error("Laser's offline model doesn't exist");
			throw e;
		}
		ACj = new DenseVector(this.alpha.size());
	}

	protected void map(Text key, RequestWritable value, Context context)
			throws IOException, InterruptedException {
		// first order offset
		Vector userFeature = value.getUserFeature();
		Vector itemFeature = value.getItemFeature();
		double firstOrder = userFeature.dot(alpha) + itemFeature.dot(beta);
		// second order offset
		for (int row = 0; row < A.numRows(); row++) {
			ACj.set(row, A.viewRow(row).dot(itemFeature));
		}
		double secondOrder = userFeature.dot(ACj);

		// 0th - offset
		// 1th - delta
		// 2th... - eta
		Vector onlineFeature = new SequentialAccessSparseVector(
				userFeature.size() + 2);
		for (Element e : userFeature.nonZeroes()) {
			onlineFeature.set(e.index() + 2, e.get());
		}
		onlineFeature.set(0, firstOrder + secondOrder);
		onlineFeature.set(1, 1.0);
		LOG.info("OnlineFeatureMapper");
		context.write(key, new VectorWritable(onlineFeature));
	}

}
