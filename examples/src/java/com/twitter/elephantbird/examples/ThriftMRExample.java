package com.twitter.elephantbird.examples;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import com.twitter.elephantbird.examples.thrift.Age;
import com.twitter.elephantbird.mapreduce.input.LzoThriftB64LineInputFormat;
import com.twitter.elephantbird.mapreduce.input.LzoThriftBlockInputFormat;
import com.twitter.elephantbird.mapreduce.io.ThriftWritable;
import com.twitter.elephantbird.mapreduce.output.LzoThriftB64LineOutputFormat;
import com.twitter.elephantbird.mapreduce.output.LzoThriftBlockOutputFormat;

/**
 * -Dthrift.test=lzoOut : takes text files with name and age on each line as 
 * input and writes to lzo file with Thrift serilized data. <br>
 * -Dthrift.test=lzoIn : does the reverse. <br><br>
 * 
 * -Dthrift.test.format=Block (or B64Line) to test different formats. <br>
 */

public class ThriftMRExample {

  private ThriftMRExample() {}

  public static class TextMapper extends Mapper<LongWritable, Text, NullWritable, ThriftWritable<Age>> {
    ThriftWritable<Age> tWritable = ThriftWritable.newInstance(Age.class);
    Age age = new Age();
    
    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
      StringTokenizer line = new StringTokenizer(value.toString(), "\t\r\n");
      if (line.hasMoreTokens()
          && age.setName(line.nextToken()) != null
          && line.hasMoreTokens()
          && age.setAge(Integer.parseInt(line.nextToken())) != null) {
          tWritable.set(age);
          context.write(null, tWritable);
      }
    }
  }

  public int runTextToLzo(String[] args, Configuration conf) throws Exception {
    Job job = new Job(conf);
    job.setJobName("Thrift Example : Text to LzoB64Line");

    job.setJarByClass(getClass());
    job.setMapperClass(TextMapper.class);
    job.setNumReduceTasks(0);
    
    job.setInputFormatClass(TextInputFormat.class);
    if (conf.get("thrift.test.format", "B64Line").equals("Block")) {
      job.setOutputFormatClass(LzoThriftBlockOutputFormat.getOutputFormatClass(Age.class, job.getConfiguration()));
    } else { // assume B64Line
      job.setOutputFormatClass(LzoThriftB64LineOutputFormat.getOutputFormatClass(Age.class, job.getConfiguration()));
    }

    FileInputFormat.setInputPaths(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));

    return job.waitForCompletion(true) ? 0 : 1;
  }

  
  public static class LzoMapper extends Mapper<LongWritable, ThriftWritable<Age>, Text, Text> {
    @Override
    protected void map(LongWritable key, ThriftWritable<Age> value, Context context) throws IOException, InterruptedException {
      Age age = value.get();
      context.write(null, new Text(age.getName() + "\t" + age.getAge()));
    }
  }

  public int runLzoToText(String[] args, Configuration conf) throws Exception {
    Job job = new Job(conf);
    job.setJobName("Thrift Example : LzoB64Line to Text");

    job.setJarByClass(getClass());
    job.setMapperClass(LzoMapper.class);
    job.setNumReduceTasks(0);
    
    if (conf.get("thrift.test.format", "B64Line").equals("Block")) {
      job.setInputFormatClass(LzoThriftBlockInputFormat.getInputFormatClass(Age.class, job.getConfiguration()));
    } else {
      job.setInputFormatClass(LzoThriftB64LineInputFormat.getInputFormatClass(Age.class, job.getConfiguration()));      
    }
    job.setOutputFormatClass(TextOutputFormat.class);

    FileInputFormat.setInputPaths(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));

    return job.waitForCompletion(true) ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    args = new GenericOptionsParser(conf, args).getRemainingArgs();
    ThriftMRExample runner = new ThriftMRExample();
    
    if (args.length != 2) {
      System.out.println("Usage: hadoop jar path/to/this.jar " + runner.getClass() + " <input dir> <output dir>");
      System.exit(1);
    }
    
    String test = conf.get("thrift.test", "lzoIn");
    
    if (test.equals("lzoIn"))
      System.exit(runner.runLzoToText(args, conf));
    if (test.equals("lzoOut"))
      System.exit(runner.runTextToLzo(args, conf));
  }
}
