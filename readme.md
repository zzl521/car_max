# SimpleVideoServer

camServer 的demo使用esp32cam自己提供http服务和视频流，会有较大性能开销。而且需要内网穿透才能外网访问。
现在提供服务端合成视频流的方式，esp32cam只需要不停拍照发送给服务端，服务端处理浏览器查看视频的请求，合成视频流给浏览器。
我们可以把服务端程序部署在公网，可以通过这样的方式远程访问。

```mermaid
graph LR
e(esp32)
S(SimpleVideoServer)
e -->|帧|S
B(浏览器)
S -->|mjpeg视频流|B
```

本项目提供了，服务端代码与esp32cam的代码。



## 运行服务端

根据自己需要选择以下任意方式运行服务端

* 下载 [windows服务端发行版](https://gitcode.net/qq_26700087/simpleVideoServer/uploads/d7a907332f17fecfe051b7174a1f2e7c/SimpleVideoSever_win.7z)

  解压之后，进入对应目录点击run.bat文件启动服务器。

* 下载[linux服务端发行版](https://gitcode.net/qq_26700087/simpleVideoServer/uploads/3e09fac66816c19f221f61b4296c0670/SimpleVideoSever_linux.7z)

  需要安装 pzip或者7zip。否则会提示7z找不到。也可以自行解压后上传。注意jre的bin目录的执行权限

  运行

  ```shell
  7z x SimpleVideoSever_linux.7z
  cd linux_release/
  sh run.sh
  ```

* 发行版内部仅仅是一些java17 版本的class文件和jre以及启动脚本，你可以使用任意其它的jre17运行这些class文件。并非需要发行版。

## 配置项

`light-video.properties`

里面含有三个配置

```properties
http.port=8003
http.clients.limit=10
stream.port=8004
```

**http.port** 为http的端口。

查看视频访问 http://ip:该端口/video。改成80则无需在url中声明端口。

充当服务端的pc访问 http://127.0.0.1:8003/video 是比较方便的一种办法。



**http.clients.limit** http客户端限制数，可以多个客户端同时查看同一视频，类似直播。增加多客户端增加服务端负担。

**stream.port** esp32cam像服务端发送照片的端口，在本项目中使用该默认端口，如果需要修改，一并修改esp32cam代码中连接的端口。一般无需修改。



## 注意事项

* <span style="color:#f00">本代码未考虑安全场景，无鉴权，也无加密。无论是抓包还是发起访问都是毫无阻挡。若需要在公网使用，请自行设计安全机制。</span>

* **src目录为服务端java源代码**

  没有其他需要可以下载上面的发型版本，已包含64位jre17，也可以使用自定义jre17运行。

  代码理论上java8也可以编译，若有需要可以自行编译。

  

* **simpleVideoRecorder目录为esp32cam的代码**

  使用platformio基于ardunio框架开发。

  使用platformio开发的同学请按实际情况修改simpleVideoRecorder 目录下的platformio.ini的串口

  

  使用ardunio IDE开发的请注意 选择开发板 为esp32cam，需要安装esp32环境，为了保险起见，你可以把源码中`main.cpp`重命名为`main.ino`

  

* 如果在局域网也可以使用PC作为服务端，查看充当服务端的PC的ip。

  打开命令行，执行ipconfig。找到wifi所在网络的网卡ip.

  

* 修改main.cpp中的wifi的ssid和密码

  
  
* 可以使用linux的同学,自行根据实际组网查看和配置网络

  
  
  