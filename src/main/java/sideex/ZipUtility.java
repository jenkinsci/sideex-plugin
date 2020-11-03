package sideex;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import hudson.FilePath;
import hudson.model.TaskListener;
/**
 * This utility compresses a list of files to standard ZIP format file.
 * It is able to compress all sub files and sub directories, recursively.
 * @author www.codejava.net
 *
 */
public class ZipUtility {
    /**
     * A constants for buffer size used to read/write data
     */
    private static final int BUFFER_SIZE = 4096;
    /**
     * Compresses a list of files to a destination zip file
     * @param listFiles A collection of files and directories
     * @param destZipFile The path of the destination zip file
     * @throws FileNotFoundException
     * @throws IOException
     * @throws InterruptedException 
     */
    public void zip(List<FilePath> listFiles, String destZipFile, TaskListener listener) throws FileNotFoundException,
            IOException, InterruptedException {
    	Path path = Paths.get(destZipFile);
    	listener.getLogger().println(path.toAbsolutePath());
        ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path));
        for (FilePath file : listFiles) {
            if (file.isDirectory()) {
                zipDirectory(file, file.getName(), zos);
            } else {
                zipFile(file, zos);
            }
        }
        zos.flush();
        zos.close();
    }
    /**
     * Compresses files represented in an array of paths
     * @param files a String array containing file paths
     * @param destZipFile The path of the destination zip file
     * @throws FileNotFoundException
     * @throws IOException
     */
//    public void zip(String[] files, String destZipFile) throws FileNotFoundException, IOException {
//        List<FilePath> listFiles = new ArrayList<FilePath>();
//        for (int i = 0; i < files.length; i++) {
//            listFiles.add(new FilePath(files[i]));
//        }
//        zip(listFiles, destZipFile);
//    }
    /**
     * Adds a directory to the current zip output stream
     * @param folder the directory to be  added
     * @param parentFolder the path of parent directory
     * @param zos the current zip output stream
     * @throws FileNotFoundException
     * @throws IOException
     * @throws InterruptedException 
     */
//    private void zipDirectory(File folder, String parentFolder,
//            ZipOutputStream zos) throws FileNotFoundException, IOException {
//    	
//    	if(folder.exists() && folder.isDirectory()) {
//    		File tempFile[] = folder.listFiles();
//    		if(tempFile != null && tempFile.length > 0) {
//		        for (File file : tempFile) {
//		            if (file.isDirectory()) {
//		                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
//		                continue;
//		            }
//		            zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
//		            BufferedInputStream bis = new BufferedInputStream(
//		                    new FileInputStream(file));
//		            long bytesRead = 0;
//		            byte[] bytesIn = new byte[BUFFER_SIZE];
//		            int read = 0;
//		            while ((read = bis.read(bytesIn)) != -1) {
//		                zos.write(bytesIn, 0, read);
//		                bytesRead += read;
//		            }
//		            zos.closeEntry();
//		            bis.close();
//		        }
//    		}
//    	}
//    }
    
    private void zipDirectory(FilePath folder, String parentFolder,
            ZipOutputStream zos) throws FileNotFoundException, IOException, InterruptedException {
    	
    	if(folder.exists() && folder.isDirectory()) {
    		List<FilePath>tempFile = folder.list();
    		if(tempFile != null && tempFile.size() > 0) {
		        for (FilePath file : tempFile) {
		            if (file.isDirectory()) {
		                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
		                continue;
		            }
		            zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
		            BufferedInputStream bis = new BufferedInputStream(file.read());
		            long bytesRead = 0;
		            byte[] bytesIn = new byte[BUFFER_SIZE];
		            int read = 0;
		            while ((read = bis.read(bytesIn)) != -1) {
		                zos.write(bytesIn, 0, read);
		                bytesRead += read;
		            }
		            zos.closeEntry();
		            bis.close();
		        }
    		}
    	}
    }
    /**
     * Adds a file to the current zip output stream
     * @param file the file to be added
     * @param zos the current zip output stream
     * @throws FileNotFoundException
     * @throws IOException
     * @throws InterruptedException 
     */
//    private void zipFile(File file, ZipOutputStream zos)
//            throws FileNotFoundException, IOException {
//        zos.putNextEntry(new ZipEntry(file.getName()));
//        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(
//                file));
//        long bytesRead = 0;
//        byte[] bytesIn = new byte[BUFFER_SIZE];
//        int read = 0;
//        while ((read = bis.read(bytesIn)) != -1) {
//            zos.write(bytesIn, 0, read);
//            bytesRead += read;
//        }
//        zos.closeEntry();
//        bis.close();
//    }
    
    private void zipFile(FilePath file, ZipOutputStream zos)
            throws FileNotFoundException, IOException, InterruptedException {
        zos.putNextEntry(new ZipEntry(file.getName()));
        BufferedInputStream bis = new BufferedInputStream(file.read());
        long bytesRead = 0;
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = bis.read(bytesIn)) != -1) {
            zos.write(bytesIn, 0, read);
            bytesRead += read;
        }
        zos.closeEntry();
        bis.close();
    }
}