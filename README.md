This Android application generates digital twin of printed documents using a pre-trained **machine learning (ML) model** to scan and extract important information from documents.

The ML model is a **MobileNet-based CNN model** trained in **TensorFlow** and quantized into a **TFLite** model for deployment on Android with acceptable latency.
Training files used in transfer learning are a combination of organic and python-generated synthetic photos.
The model requires pre-defined document types which are also used during training and the user is expected to select the correct document type prior to or during automatic scan via horizontal scrolling on the camera UI.

The application uses **model-view-controller** pattern to manage/show the scanned and stored documents.

Here is a video showing document scanning in action:

![Alt Text](readme-video.gif)
