package com.example.promar.imageprocessinglib.feature;

import com.example.promar.imageprocessinglib.model.ImageFeature;
import org.opencv.core.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.Locale;
import java.util.Scanner;


public class FeatureStorage {
    static final String TYPE_ID = "type_id";
    static final String MAT_TYPE_ID = "opencv-matrix";
    static final String KEYPOINTS_TYPE_ID = "opencv-keypoints";
    static final String KP_TAG = "kp";  //Tag for every KeyPoint in MatOfKeyPoint
//    Context context;

    public enum FeatureStorageFlag {
        READ, WRITE;
    }

    // varaible
    private File file;
    private boolean isWrite;
    private Document doc;
    private Element rootElement;

    public FeatureStorage() {
//    public FeatureStorage(Context context) {
        file = null;
        isWrite = false;
        doc = null;
        rootElement = null;
//        this.context = context;
    }


    // read or write
    public void open(String filePath, FeatureStorageFlag flags ) {
        try {
            if( flags == FeatureStorageFlag.READ ) {
                open(filePath);
            }
            else {
                create(filePath);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    // read only
    public void open(String filePath) {
        try {
            file = new File(filePath);
            if( file == null || file.isFile() == false ) {
                System.err.println("Can not open file: " + filePath );
            }
            else {
                isWrite = false;
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
                doc.getDocumentElement().normalize();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    // write only
    public void create(String filePath) {
        try {
            file = new File(filePath);
            //if file exists, overwrite it
            file.createNewFile();
            if( file == null ) {
                System.err.println("Can not wrtie file: " + filePath );
            }
            else {
                isWrite = true;
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

                rootElement = doc.createElement("opencv_storage");
                doc.appendChild(rootElement);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public MatOfKeyPoint readKeyPoints(String tag) {
        if( isWrite ) {
            System.err.println("Try read from file with write flags");
            return null;
        }

        NodeList nodelist = doc.getElementsByTagName(tag);
        MatOfKeyPoint readKPs = new MatOfKeyPoint();

        for( int i = 0 ; i<nodelist.getLength() ; i++ ) {
            Node node = nodelist.item(i);

            if( node.getNodeType() == Node.ELEMENT_NODE ) {
                Element element = (Element)node;

                String type_id = element.getAttribute(TYPE_ID);
                if( KEYPOINTS_TYPE_ID.equals(type_id) == false) {
                    System.out.println("Fault type_id ");
                }

                //retrieve KeyPoint
                NodeList kpNodes = element.getElementsByTagName(KP_TAG);
                KeyPoint[] kpArray = new KeyPoint[kpNodes.getLength()];

                for (int d = 0; d < kpNodes.getLength(); d++) {
                    Element kp = (Element)kpNodes.item(d);

                    String classId = kp.getElementsByTagName("class_id").item(0).getTextContent();
                    String x = kp.getElementsByTagName("x").item(0).getTextContent();
                    String y = kp.getElementsByTagName("y").item(0).getTextContent();
                    String size = kp.getElementsByTagName("size").item(0).getTextContent();
                    String angle = kp.getElementsByTagName("angle").item(0).getTextContent();
                    String octave = kp.getElementsByTagName("octave").item(0).getTextContent();
                    String response = kp.getElementsByTagName("response").item(0).getTextContent();

                    KeyPoint keyPoint = new KeyPoint(
                            Float.parseFloat(x),
                            Float.parseFloat(y),
                            Float.parseFloat(size),
                            Float.parseFloat(angle),
                            Float.parseFloat(response),
                            Integer.parseInt(octave),
                            Integer.parseInt(classId));
                    kpArray[d] = keyPoint;
                }
                readKPs.fromArray(kpArray);
            }
        }
        return readKPs;
    }

    public void writeKeyPoints(String tag, MatOfKeyPoint keyPoints) {
        try {
            if( isWrite == false) {
                System.err.println("Try write to file with no write flags");
                return;
            }

            Element kpsRoot = doc.createElement(tag);
            kpsRoot.setAttribute(TYPE_ID, KEYPOINTS_TYPE_ID);
            rootElement.appendChild(kpsRoot);
            KeyPoint[] kps = keyPoints.toArray();

            //parse every KeyPoint
            for (KeyPoint kp : kps) {
                Element k = doc.createElement(KP_TAG);

                Element classId = doc.createElement("class_id");
                classId.appendChild( doc.createTextNode( String.valueOf(kp.class_id) ));

                Element x = doc.createElement("x");
                x.appendChild( doc.createTextNode( String.valueOf(kp.pt.x) ));

                Element y = doc.createElement("y");
                y.appendChild( doc.createTextNode( String.valueOf(kp.pt.y) ));

                Element size = doc.createElement("size");
                size.appendChild( doc.createTextNode( String.valueOf(kp.size) ));

                Element angle = doc.createElement("angle");
                angle.appendChild( doc.createTextNode( String.valueOf(kp.angle) ));

                Element octave = doc.createElement("octave");
                octave.appendChild( doc.createTextNode( String.valueOf(kp.octave) ));

                Element response = doc.createElement("response");
                response.appendChild( doc.createTextNode( String.valueOf(kp.response) ));

                k.appendChild(classId);
                k.appendChild(x);
                k.appendChild(y);
                k.appendChild(size);
                k.appendChild(angle);
                k.appendChild(octave);
                k.appendChild(response);

                kpsRoot.appendChild(k);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    public Mat readMat(String tag) {
        if( isWrite ) {
            System.err.println("Try read from file with write flags");
            return null;
        }

        NodeList nodelist = doc.getElementsByTagName(tag);
        Mat readMat = null;

        for( int i = 0 ; i<nodelist.getLength() ; i++ ) {
            Node node = nodelist.item(i);

            if( node.getNodeType() == Node.ELEMENT_NODE ) {
                Element element = (Element)node;

                String type_id = element.getAttribute(TYPE_ID);
                if( MAT_TYPE_ID.equals(type_id) == false) {
                    System.out.println("Fault type_id ");
                }

                String rowsStr = element.getElementsByTagName("rows").item(0).getTextContent();
                String colsStr = element.getElementsByTagName("cols").item(0).getTextContent();
                String dtStr = element.getElementsByTagName("dt").item(0).getTextContent();
                String dataStr = element.getElementsByTagName("data").item(0).getTextContent();

                int rows = Integer.parseInt(rowsStr);
                int cols = Integer.parseInt(colsStr);
                int type = CvType.CV_8U;

                Scanner s = new Scanner(dataStr);
                s.useLocale(Locale.US);

                if( "f".equals(dtStr) ) {
                    type = CvType.CV_32F;
                    readMat = new Mat( rows, cols, type );
                    float fs[] = new float[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextFloat() ) {
                                fs[0] = s.nextFloat();
                            }
                            else {
                                fs[0] = 0;
                                System.err.println("Unmatched number of float value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, fs);
                        }
                    }
                }
                else if( "i".equals(dtStr) ) {
                    type = CvType.CV_32S;
                    readMat = new Mat( rows, cols, type );
                    int is[] = new int[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextInt() ) {
                                is[0] = s.nextInt();
                            }
                            else {
                                is[0] = 0;
                                System.err.println("Unmatched number of int value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, is);
                        }
                    }
                }
                else if( "s".equals(dtStr) ) {
                    type = CvType.CV_16S;
                    readMat = new Mat( rows, cols, type );
                    short ss[] = new short[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextShort() ) {
                                ss[0] = s.nextShort();
                            }
                            else {
                                ss[0] = 0;
                                System.err.println("Unmatched number of int value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, ss);
                        }
                    }
                }
                else if( "b".equals(dtStr) ) {
                    readMat = new Mat( rows, cols, type );
                    byte bs[] = new byte[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextByte() ) {
                                bs[0] = s.nextByte();
                            }
                            else {
                                bs[0] = 0;
                                System.err.println("Unmatched number of byte value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, bs);
                        }
                    }
                }
            }
        }
        return readMat;
    }

    public void writeMat(String tag, Mat mat) {
        try {
            if( isWrite == false) {
                System.err.println("Try write to file with no write flags");
                return;
            }

            Element matrix = doc.createElement(tag);
            matrix.setAttribute(TYPE_ID, MAT_TYPE_ID);
            rootElement.appendChild(matrix);

            Element rows = doc.createElement("rows");
            rows.appendChild( doc.createTextNode( String.valueOf(mat.rows()) ));

            Element cols = doc.createElement("cols");
            cols.appendChild( doc.createTextNode( String.valueOf(mat.cols()) ));

            Element dt = doc.createElement("dt");
            String dtStr;
            int type = mat.type();
            if(type == CvType.CV_32F ) { // type == CvType.CV_32FC1
                dtStr = "f";
            }
            else if( type == CvType.CV_32S ) { // type == CvType.CV_32SC1
                dtStr = "i";
            }
            else if( type == CvType.CV_16S  ) { // type == CvType.CV_16SC1
                dtStr = "s";
            }
            else if( type == CvType.CV_8U ){ // type == CvType.CV_8UC1
                dtStr = "b";
            }
            else {
                dtStr = "unknown";
            }
            dt.appendChild( doc.createTextNode( dtStr ));

            Element data = doc.createElement("data");
            String dataStr = dataStringBuilder( mat );
            data.appendChild( doc.createTextNode( dataStr ));

            // append all to matrix
            matrix.appendChild( rows );
            matrix.appendChild( cols );
            matrix.appendChild( dt );
            matrix.appendChild( data );

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private String dataStringBuilder(Mat mat) {
        StringBuilder sb = new StringBuilder();
        int rows = mat.rows();
        int cols = mat.cols();
        int type = mat.type();

        if( type == CvType.CV_32F ) {
            float fs[] = new float[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, fs);
                    sb.append( String.valueOf(fs[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }
        else if( type == CvType.CV_32S ) {
            int is[] = new int[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, is);
                    sb.append( String.valueOf(is[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }
        else if( type == CvType.CV_16S ) {
            short ss[] = new short[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, ss);
                    sb.append( String.valueOf(ss[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }
        else if( type == CvType.CV_8U ) {
            byte bs[] = new byte[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, bs);
                    sb.append( String.valueOf(bs[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }
        else {
            sb.append("unknown type\n");
        }

        return sb.toString();
    }


    public void release() {
        try {
            if( isWrite == false) {
                System.err.println("Try release of file with no write flags");
                return;
            }

            DOMSource source = new DOMSource(doc);

            StreamResult result = new StreamResult(file);

            // write to xml file
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // do it
            transformer.transform(source, result);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public void saveFPtoFile(String file, ImageFeature imageFeature ){
        open(file, FeatureStorage.FeatureStorageFlag.WRITE);
        writeMat("des", imageFeature.getDescriptors());
        writeKeyPoints("keypoint", imageFeature.getObjectKeypoints());
        release();
    }

    public ImageFeature loadFPfromFile(String filePath){
        open(filePath);
        Mat des = readMat("des");
        MatOfKeyPoint kps = readKeyPoints("keypoint");
        return new ImageFeature(kps, des);
    }
}
