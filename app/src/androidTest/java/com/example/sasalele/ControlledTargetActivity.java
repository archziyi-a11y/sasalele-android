package com.example.sasalele;
import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
public class ControlledTargetActivity extends Activity {
 private int count=0;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(-1);root.setPadding(32,96,32,0);
  TextView title=new TextView(this);String label="受控独立目标";
  java.io.File labelFile=new java.io.File(getNoBackupFilesDir(),"capture-label.txt");
  if(labelFile.exists()) try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(labelFile), java.nio.charset.StandardCharsets.UTF_8))){label=reader.readLine();}catch(java.io.IOException ignored){}
  title.setText(label);title.setTextSize(30);title.setTextColor(0xff000000);root.addView(title);
  TextView description=new TextView(this);description.setText("合成独立测试页，不是真实聊天");description.setTextSize(18);description.setTextColor(0xff000000);root.addView(description);
  Button button=new Button(this);button.setText("底层触点计数 0");button.setOnClickListener(v->{count++;button.setText("底层触点计数 "+count);});root.addView(button);setContentView(root);
 }
}
