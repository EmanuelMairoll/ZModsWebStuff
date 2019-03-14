import org.objectweb.asm.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class ASMStringReplacer {

	public static boolean doPatch(File src, File dst, String toReplace, String replaceWith) {
		if (!src.getAbsolutePath().endsWith(".jar")) {
			return false;
		}

		try {
			dst.delete();

			JarFile jarFileIn = new JarFile(src);
			JarOutputStream jarFileOut = new JarOutputStream(new FileOutputStream(dst));

			Enumeration<JarEntry> entries = jarFileIn.entries();

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String entryName = entry.getName();

				try (InputStream inputStream = jarFileIn.getInputStream(entry)) {
					jarFileOut.putNextEntry(new JarEntry(entryName));

					if (entryName.endsWith(".class")) {
						ClassReader reader = new ClassReader(inputStream);
						ClassWriter writer = new ClassWriter(0);

						ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
							@Override
							public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
								return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, descriptor, signature, exceptions)) {
									@Override
									public void visitLdcInsn(Object value) {
										if (value instanceof String) {
											super.visitLdcInsn(((String) value).replace(toReplace, replaceWith));
										} else {
											super.visitLdcInsn(value);
										}
									}
								};
							}

							@Override
							public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object cst) {
								if (cst instanceof String) {
									return super.visitField(access, name, descriptor, signature, ((String) cst).replace(toReplace, replaceWith));
								} else {
									return super.visitField(access, name, descriptor, signature, cst);
								}
							}
						};

						reader.accept(visitor, 0);

						jarFileOut.write(writer.toByteArray());
					} else {
						byte[] data = new byte[1024];
						int amount = inputStream.read(data);
						while (amount > 0) {
							jarFileOut.write(data, 0, amount);
							amount = inputStream.read(data);
						}
					}
					jarFileOut.closeEntry();
				}
			}
			jarFileIn.close();
			jarFileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

}