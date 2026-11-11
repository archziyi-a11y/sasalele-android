package com.example.sasalele

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

class MaterialImagePreviewActivity:ComponentActivity() {
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);AcquisitionUi.hidden.value=true
  val uri=intent.getStringExtra("image_uri")?.let(Uri::parse)
  if(uri==null || uri.scheme!="content" || uri.authority!="$packageName.fileprovider"){finish();return}
  setContent {com.example.sasalele.ui.theme.SASALELETheme {
   var scale by remember{mutableFloatStateOf(1f)}
   var x by remember{mutableFloatStateOf(0f)};var y by remember{mutableFloatStateOf(0f)}
   Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Text(intent.getStringExtra("image_title") ?: "图片预览",Modifier.weight(1f).padding(12.dp),color=Color.White,maxLines=2)
     TextButton(onClick={finish()}){Text("关闭预览",color=Color.White)}
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
     val width=constraints.maxWidth.toFloat();val height=constraints.maxHeight.toFloat()
     AsyncImage(model=uri,contentDescription="放大的素材图片",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize()
      .pointerInput(Unit){detectTransformGestures{_,pan,zoom,_->
       scale=(scale*zoom).coerceIn(1f,6f)
       x=(x+pan.x).coerceIn(-width*(scale-1)/2,width*(scale-1)/2)
       y=(y+pan.y).coerceIn(-height*(scale-1)/2,height*(scale-1)/2)
      }}
      .pointerInput(Unit){detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2.5f;x=0f;y=0f})}
      .graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y})
    }
    Text("双指缩放 · 放大后拖动 · 双击放大或还原",Modifier.align(Alignment.CenterHorizontally).padding(12.dp),color=Color.White)
   }
  }}
 }
 override fun onDestroy(){if(isFinishing)AcquisitionUi.hidden.value=false;super.onDestroy()}
}
