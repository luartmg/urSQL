package urSQL.StoredDataManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import urSQL.StoredDataManager.BplusJ.*;
import urSQL.System.TableAttribute;
import urSQL.System.TableMetadata;

/**
 * Objeto que administra la forma de adminstrar los 
 * archivos de las tablas y las bases de datos
 * 
 * @author Andres Brais 
 *
 */
public class StoreDataManager {
	/*******************STRINGS PARA ARCHIVOS***********************/
	
	/**
	 * Separador de en las direcciones de archivos
	 */
	private static final String FILE_SEPARATOR = File.separator;
	/**
	 * Este es la direcci�n donde se van a almacenar las bases de datos.
	 */
	private static final String DATABASES_PATH = System.getProperty("user.dir") + FILE_SEPARATOR +"DATABASES";
	/**
	 * Sufijo del nombre de los archivos de arbol
	 */
	private static final String TREE_SUFIX = "_TREE";
	/**
	 * Sufijo del nombre de los archivos de bloques de informacion
	 */
	private static final String BLOCKS_SUFFIX = "_BLOCKS";
	
	/**************************TIPOS EN BYTES******************************/
	
	/**
	 * Byte, se escribe cuando una columna es nula
	 */
	private static final byte NULL_VALUE = (byte)0xAA;
	/**
	 * Byte de tipo entero
	 */
	private static final byte BY_TYPE_INTEGER = (byte)0x00;
	/**
	 * Byte de tipo char
	 */
	private static final byte BY_TYPE_CHAR = (byte)0x02;
	/**
	 * Byte de tipo varchar
	 */
	private static final byte BY_TYPE_VARCHAR = (byte)0x03;
	/**
	 * Byte de tipo decimal, como el float
	 */
	private static final byte BY_TYPE_DECIMAL = (byte)0x01;
	/**
	 * Byte de tipo fecha
	 */
	private static final byte BY_TYPE_DATETIME = (byte)0x04;
	
	/**************************KEYS DE CONTROL****************************/
	
	/**
	 * Ubica la posicion de la cantidad de columnas 
	 */
	private static final String COLUMN_QUANTITY_KEY = " COLQ";
	/**
	 * Ubica el indice de la llave primaria
	 */
	private static final String PK_INDEX = " PK";
	
	/**
	 * Indice donde se almacena la 
	 * metadatada para ser recuperada
	 */
	private static final String METADATA_KEY = " METADATA";
	
	/**
	 * 
	 */
	private String database_name="Basesita";
	
	/**
	 * Crea la carpeta de las bases de datos si estas no existen.
	 */
	public StoreDataManager(){
		//Archivo de donde esta la base
		File databases_dir = new File(DATABASES_PATH);
		
		//si el arhivo no existe lo crea 
		if(!databases_dir.exists()){
			boolean result = false;
			result = databases_dir.mkdirs();
			//si no se pudo crear env�a un mensaje de error
			if(!result){
				System.err.format("Hubo un problema al tratar de crear los siguientes directorios %s\n", 
						StoreDataManager.DATABASES_PATH);
			}
			
		}
	}
	
	
	/**
	 * Crea la nueva base de datos 
	 * 
	 * @param database_name nombre de nueva base de 
	 * datos 
	 */
	public void createDatabase(String database_name){
		//se crea el archivo de donde iria el archivo
		File database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		//si la base de datos ya esta creada
		if(database.exists()){
			System.err.format("La base de datos con el nombre %s ya ha sido creada\n", database_name);
		}
		//si no crea el archivo 
		else{
			boolean result = database.mkdirs();
			if(result){
				System.out.format("La base de datos %s fue creada correctamente\n", database_name);
			}else{
				System.err.format("Hubo un error al crear la base de datos %s\n", database_name);
			}
		}
	}
	
	/**
	 * Elimina una base de datos
	 * 
	 * @param database_name Nombre de la base a eliminar
	 */
	public void deleteDatabaseScheme(String database_name){
		//se crea el archivo de donde iria el archivo
		File database = new File(StoreDataManager.DATABASES_PATH + "/" + database_name);
		//se verifica is existe
		if(!database.exists()){
			System.err.format("La base de datos %s no existe en las bases de datos almacenadas\n", database_name);
		}
		//si si existe se procede a elimnar
		else{
			//si lo que se va a borrar no es un directorio
			if(!database.isDirectory()){
				System.err.format("La direccion especificada %s, no corresponde a un directorio\n" , database_name);
			}
			//si cumple con ser un directorio
			else{
				recursiveFileDelete(database);
			}
		}
	}
	
	/**
	 * Elimina la carpeta, elimimna los archivos dentro
	 * si es necesario o si hay en el directorio.
	 * 
	 * @param database Nombre de la base de datos a 
	 * eliminar.
	 */
	private void recursiveFileDelete(File database){
		//Esta pila es utilizada para eliminar los archivos 
		//dentro de los directorios
		java.util.Stack<File> stack = new java.util.Stack<File>();
		//Se agrega el directorio inicial
		stack.push(database);
		
		while(!stack.isEmpty()){
			File tmp = stack.pop();
			//si es un archivo se borra inmediatamente
			if(tmp.isFile()){
				tmp.delete();
			}
			//si es un directorio
			else if(tmp.isDirectory()){
				File[] tmp_array = tmp.listFiles();
				//si no es directorio o un error de I/O
				if(tmp_array == null){
					System.err.format("El archivo de nombre %s, no es un directorio o hubo un error de I/O"
							+ "\nla direccion abosulta es %s\n", 
							tmp.getName(), tmp.getAbsolutePath());
					break;
				}
				//si el directorio esta vacio se elimina inmediatamente
				else if(tmp_array.length == 0){
					tmp.delete();
				}
				//si el directorio esta lleno
				else if(tmp_array.length > 0){
					stack.push(tmp);
					
					for (int i = 0; i < tmp_array.length; i++) {
						//si es un archivo se elimina directamente
						if(tmp_array[i].isFile()){
							tmp_array[i].delete();
						}
						//si es un directorio se agrega a la pila
						else if(tmp_array[i].isDirectory()){
							stack.push(tmp_array[i]);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Elige cual es la base de datos actual
	 * 
	 * @param database_name nombre de la base de datos
	 */
	public void setDatabase(String database_name){
		File database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		
		if(!database.exists()){
			System.err.format("La base de datos %s no existe\n", database_name);
		}
		else{
			this.database_name = database_name;
		}
	}
	
	/**
	 * Crea una table en una base de datos existente.
	 * 
	 * @param table_name nombre de la tabla a crear.
	 * 
	 * @param col_quant cantidad de columnas de la tabla.
	 * 
	 * @param pk_index posicion del vector donde se 
	 * encuentra la llave primaria.
	 * 
	 * @param dabase_name nombre de la base de datos
	 * en la que se va a crear la tabla.
	 */
	public void createTable(TableMetadata metadata){
		//nombre de la base de datos
		String table_name  = metadata.getTableName();
		//se crea el directorio a ver si existe
		File database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		
		if(!database.exists()){
			System.err.format("La base de datos con el nombre %s\n"
					+ "no se encuentra o no ha sido creada\n", database_name);
		}
		//Si el archivo existe
		else if(database.exists()){
			//se crea la tabla que sigue
			File table = new File(database, table_name);
			//se comprueba si existe
			if(table.exists()){
				System.err.format("La tabla %s ya habia sido creada\n", table_name);
			}
			//si no existe
			else{
				table.mkdirs();
				//Crea los archivos de los arboles 
				File tree_file = new File(table, table_name + TREE_SUFIX);
				File block_file = new File(table, table_name + BLOCKS_SUFFIX);
				
				try {
					//crea el arbol
					xBplusTreeBytes tree = xBplusTreeBytes.Initialize(new RandomAccessFile(tree_file, "rw"),
							new RandomAccessFile(block_file, "rw"), 10);
					//guarda en el arbol la cantidad de columnas 
					short colq_q_sh = (short) metadata.getTableColumns().size();
					byte[] colq_quant_by = short2bytes(colq_q_sh);
					
					tree.set(COLUMN_QUANTITY_KEY, colq_quant_by);
					
					//agrega el indice de la llave primaria
					short pk_index_sh = (short) metadata.getTableColumns().indexOf(metadata.getPrimaryKey());
					byte[] pk_index_by = short2bytes(pk_index_sh);
					
					tree.set(PK_INDEX, pk_index_by);
					
					byte[] columns_metadata = writeMetadata(metadata);
					
					tree.set(METADATA_KEY, columns_metadata);
					
					
					//se envia todo al arbol
					tree.Commit();
					
					if(tree.ContainsKey(COLUMN_QUANTITY_KEY) && tree.ContainsKey(PK_INDEX) &&
							tree.ContainsKey(METADATA_KEY)){
						System.out.format("La tabla %s ha sido creada correctamente\n", table_name);
					}else{
						System.err.format("Hubo un problema al crear la tabla %s\n", table_name);
					}
					
					//cierra el arbol
					tree.Shutdown();
					
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.format("Hubo un error al crear el archivo de la tabla %s\n", table_name);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.format("Hubo un error satanico al crear el arbol de la tabla %s\n", table_name);
				}
			}
		
		}
	}

	/**
	 * Este metodo convierte la lista de atributos de tabla y los 
	 * pasa a arreglos de datos
	 * 
	 * @param metadata informaci�n que uno le importa 
	 * 
	 * @return byte[]con el LinkedList
	 */
	private byte[] writeMetadata(TableMetadata metadata){
		//se saca un iterador de la lista de atributos
		Iterator<TableAttribute> iterator = metadata.getTableColumns().iterator();
		//array que a a ser el resultado
		byte[] result = attribute2bytes(iterator.next());
		
		while (iterator.hasNext()) {
			TableAttribute attribute = iterator.next();
			
			byte[] tmp_array = attribute2bytes(attribute);
			//se concatenan ambos array para crear uno con toda la
			//metadata
			result = byteArrayConcatenate(result, tmp_array);
			
		}
		
		return result;
	}
	
	/**
	 * Convierte los atributos a bytes 
	 * 
	 * @param attribute atributo a convertir a bytes
	 * 
	 * @return un arreglo de bytes con toda la 
	 * informacion dle registro
	 */
	private byte[] attribute2bytes(TableAttribute attribute){
		byte[] byte_type = new byte[1];
		switch(attribute.getType()){
			case TableAttribute.TYPE_INT:
				byte_type[0] = BY_TYPE_INTEGER;
				break;
			case TableAttribute.TYPE_DECIMAL:
				byte_type[0] = BY_TYPE_DECIMAL;
				break;
			case TableAttribute.TYPE_CHAR:
				byte_type[0] = BY_TYPE_CHAR;
				break;
			case TableAttribute.TYPE_VARCHAR:
				byte_type[0] = BY_TYPE_VARCHAR;
				break;
			case TableAttribute.TYPE_DATETIME:
				byte_type[0] = BY_TYPE_DATETIME;
				break;
		}
		byte[] by_str = attribute.getName().getBytes();
		//bytes del registro y el tipo 
		byte[] reg =  byteArrayConcatenate(byte_type, by_str);
		
		short size = (short)(reg.length-1);
		//cuantos bytes ocupa el registro
		byte[] by_size = short2bytes(size);
		
		return byteArrayConcatenate(by_size, reg);
	}
	
	
	/**
	 * Agrega una fila en una tabla de una base de datos.
	 *  
	 * @param table_name Nombre de la tabla.
	 * 
	 * @param vec Vector qeu contiene los tipos y datos a 
	 * insertar en la tabla.
	 */
	public void insertRow(TableMetadata metadata,LinkedList<String> data){
		//se verifica que exista la carpeta de bases de datos
		File file_database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		//nombre de la tabla
		String table_name = metadata.getTableName();
		if(!file_database.exists()){
			//la base de datos no existe
			System.err.format("La base de datos con el nombre %s no ha sido creada\n", database_name);
		}
		//si el archivo existe
		else{
			//se crea la carpeta de la tabla
			File file_table = new File(file_database, table_name);
			if(!file_table.exists()){
				System.err.format("La tabla %s no se encuentra en la base de datos %s\n", table_name, database_name);
			}else{
				//se verifica que existan los archivos de las tablas 
				File file_blocks = new File(file_table, table_name + BLOCKS_SUFFIX);
				File file_tree = new File(file_table, table_name + TREE_SUFIX);
				//se comprueba que existan los archivos
				if(!file_blocks.exists() || !file_tree.exists()){
					System.err.format("La tabla con el nombre %s no ha sido creada\n"
							+ "o algun archivo a sido corrompido\n", table_name);
				}
				else{
					//si existen se crea el arbol
					try {
						xBplusTreeBytes tree = xBplusTreeBytes.ReOpen(new RandomAccessFile(file_tree, "rw"), 
								new RandomAccessFile(file_blocks, "rw"));
						//se obtiene el indice de la llave primaria
						byte[] b_pk_index = tree.get(PK_INDEX);
						int pk_index = (int)ByteBuffer.wrap(b_pk_index).getShort();
						
						String key = data.get(pk_index);
						
						if(metadata.getTableColumns().size() != data.size()){
							System.err.format("La fila debe tener %d columnas \n", metadata.getTableColumns().size());
						}
						//si cumple con la cantidad de columnas
						else {
							//Si la llave primaria es nula
							if(key.compareTo("null") == 0){
								System.err.format("La llave primaria de la fila es nula\n");
								tree.Shutdown();
							}
							//si la llave ya esta
							else if(tree.ContainsKey(key)){
								System.err.format("La llave primaria ya se encuentra en el arbol\n");
								tree.Shutdown();
							}
							//inserta si no hay problema con la llave
							else{
								//se cre aun vector con pares de tipo y datos 
								//a partir de el data y metadata
								Vector<Pair<String,String>> vec = convert2vec(metadata.getTableColumns(), data);
								//crea el registro de bytes
								byte[] register = toBytes(vec);
								//se escribe en el arbol la fila
								tree.set(key, register);
								//se verifica que se haya insertado
								if(!tree.ContainsKey(key)){
									System.err.format("La fila de llave %s de la tabla %s no se pudo insertar correctamente\n",
											key, table_name);
								}
								else{
									tree.Commit();
									tree.Shutdown();
								}
								
							}
						}
						
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			
		}
		
	}
	
	/**
	 * A partir de los datos y el metadata crea un vector donde une 
	 * los datos con el tipo de dato
	 * 
	 * @param linkedList Informaci�n de las columnas de la tabla 
	 * 
	 * @param data informaci�n que se va a guardar en la tabla
	 * 
	 * @return Vector de {@link Pair<{@link String},{@link String}>}
	 */
	private Vector<Pair<String, String>> convert2vec(LinkedList<TableAttribute> linkedList, 
			LinkedList<String> data){
		//crea el nuevo vector
		Vector<Pair<String, String>> result = new Vector<Pair<String, String>>();
		//iterator de la lista
		Iterator<TableAttribute> iterator = linkedList.iterator();
		//empareja tipo con el dato
		for (int i = 0; i < data.size() && iterator.hasNext(); i++) {
			TableAttribute tatt = iterator.next();
			Pair<String, String> tmp = new Pair<String,String>(tatt.getType(), data.get(i));
			result.addElement(tmp);
		}
		//retorna un vector de pares
		return result;
	}
	
	
	/**
	 * Pasa a bytes la informaci�n que se quiere almacenar
	 * en la tabla.
	 * 
	 * @param vec Vector con el pares de string donde 
	 * el primer objeto es el tipo del objeto, y el 
	 * segundo es la informaci�n a almacenar
	 * 
	 * @return un arreglo de bytes con toda la informaci�n
	 */
	private byte[] toBytes(Vector<Pair<String,String>> vec){
		//crea un nuevo vector donde se alamacenan los arreglos
		//de bytes para ser concatenados
		Vector<byte[]> res = new Vector<byte[]>();
		//toma todos los datos y los convierte a  bytes
		for (int i = 0; i < vec.size(); i++) {
			String type = vec.get(i).getFirst();
			
			String data = vec.get(i).getSecond();
			
			byte[] tmp_r = toBytesAux(type,data);
					
			res.add(tmp_r);
		}
		//concatena todos los arreglos
		return concatenateByteArray(res);
	}
	
	/**
	 * Se pasan todos los tipos con su respectivo encabezado
	 * para ser leidos cuando son extra�dos
	 * 
	 * @param type Tipo de informaci�n que se almacena dentro
	 * en el registro
	 * 
	 * @param data informaci�n que se almacena en el registro
	 * 
	 * @return un arreglo de bytes que representa al registro 
	 * completo
	 */
	private byte[] toBytesAux(String type, String data){
		switch(type){
			case TableAttribute.TYPE_INT:
				if(data.compareTo("null") == 0){
					byte[] nullbyte0 = new byte[1];
					nullbyte0[0] = NULL_VALUE;
					return makeRegister(NULL_VALUE, nullbyte0.length, nullbyte0);
				}else{
					int integer = Integer.parseInt(data);
					byte[] integer_array = int2bytes(integer);
					return makeRegister(BY_TYPE_INTEGER, integer_array.length, integer_array);
				}
				
				
			case TableAttribute.TYPE_DECIMAL:
				float float_value = Float.parseFloat(data);
				byte[] float_array = float2bytes(float_value);
				return makeRegister(BY_TYPE_DECIMAL, float_array.length, float_array);
				
			case TableAttribute.TYPE_CHAR:
				byte[] char_array = data.getBytes();
				return makeRegister(BY_TYPE_CHAR, char_array.length, char_array);
				
			case TableAttribute.TYPE_DATETIME:
				byte[] date_array = data.getBytes();
				return makeRegister(BY_TYPE_DATETIME, date_array.length, date_array);
				
			default:
				byte[] string_array = data.getBytes();
				return makeRegister(BY_TYPE_VARCHAR, string_array.length, string_array);
		}
	}
	
	/**
	 * Retorna un arreglo de bytes donde se concatenaron todos
	 * los subregistros de informaci�n con su debido enccabezado
	 * 
	 * @param bytes vector con todos los arreglos de bytes que 
	 * van a ser concatenados.
	 * 
	 * @return un arreglo de bytes cono resultado de la concatenaci�n
	 * de todos los arreglos de bytes
	 */
	private byte[] concatenateByteArray(Vector<byte[]> bytes){
		//tama�o total del arreglo de bytes
		int total_size = 0;

		for (int i = 0; i < bytes.size(); i++) {
			total_size += bytes.get(i).length;
		}
		//se crea el arreglo con el tama�o total
		byte[] register = new byte[total_size];
		//indice que se lleva sobre el arreglo final
		int global_index = 0;
		
		for (int i = 0; i < bytes.size(); i++) {
			byte[] sub_register = bytes.get(i);
			
			for (int j = 0; j < sub_register.length; j++) {
				register[global_index+j] = sub_register[j];
			}
			global_index += sub_register.length;
		}
		
		return register;
		
	}
	
	/**
	 * Crea el registro que se de la fila
	 * 
	 * @param type tipo de la informaci�n que se va a guardar
	 * en el registro
	 * 
	 * @param size cantidad de bytes que va a ocupar el registro
	 * 
	 * @param data informaci�n del registro 
	 * 
	 * @return arreglo de bytes donde se contiene la informacion,
	 * el tama�o y el tipo de informacion guardada
	 */
	private byte[] makeRegister(byte type, int size, byte[] data){
		//se crea el registro donde se almacena  
		byte[] reg = new byte[3+data.length];
		//se agrega primero el tipo
		reg[0] = type;
		//se agrega el tama�o
		byte[] by_size = short2bytes((short)size);
		reg[1] = by_size[0];
		reg[2] = by_size[1];
		//se agrega la informaci�n
		for (int i = 0; i < data.length; i++) {
			reg[i+3] = data[i];
		}
		
		return reg;
	}
	
	/**
	 * Busca una fila en el arbol por la llave primaria 
	 * y la devuelve en String
	 * 
	 * @param pk Llave primaria
	 * 
	 * @param database_name nombre de la base de datos 
	 * en la que se encuentra la tabla
	 * 
	 * @param table_name tabla a la que se le va a 
	 * leer una fila
	 * 
	 * @return String con la fila.
	 */
	public String getRow(String pk, String table_name){
		String result = "";
		//se verifica que exista la carpeta de bases de datos
		File file_database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		if(!file_database.exists()){
		//la base de datos no existe
			System.err.format("La base de datos con el nombre %s no ha sido creada\n", database_name);
		}
		//si la base existe
		else{
			File file_table = new File(file_database, table_name);
			//si el archivo no existe
			if(!file_table.exists()){
				System.err.format("La tabla %s no existe en la base %s\n", table_name, database_name);
			}
			//si el archivo existe.
			else{
				//se verifica que existan los archivos de las tablas 
				File file_blocks = new File(file_table, table_name + BLOCKS_SUFFIX);
				File file_tree = new File(file_table, table_name + TREE_SUFIX);
				if(!file_blocks.exists() || !file_tree.exists()){
					System.err.format("La tabla con el nombre %s no ha sido creada\n"
							+ "o algun archivo a sido corrompido\n", table_name);
				}
				//si existe
				else{
					//si existen los archivos se crea el arbol
					try {
						xBplusTreeBytes tree = xBplusTreeBytes.ReOpen(new RandomAccessFile(file_tree, "rw"), 
								new RandomAccessFile(file_blocks, "rw"));
						//comprueba que la llave se encuentre en el arbol
						if(!tree.ContainsKey(pk)){
							System.err.format("En la tabla %s de la base de datos %s no se\n"
									+ "encuentra la llave primaria %s\n", table_name, database_name, pk);
						}
						//si se encuentra toma el registro
						else{
							byte[] register = tree.get(pk);
							//apaga el arbol
							tree.Shutdown();
							//se convierte a string
							result = parseByteArray2String(register);
						}
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		
		return result;
	}
	
	/**
	 * Convierte el registro de byte en un string
	 * contiendo los valores de todos las columnas
	 * 
	 * @param bytes registro que se va a parsear
	 * 
	 * @return String con la forma de la fila
	 */
	private String parseByteArray2String(byte[] bytes){
		String result = "";
		//indice por el cual va 
		int current_index = 0;
		//offset de por donde se va a leer
		int offset = 3;
		//cantidad de bytes que se van a leer
		int length = 0;
		//tipo de valor del que se va a leer
		byte type = (byte)0x00;
		
		while(current_index < bytes.length){
			//se toma el tipo
			type = bytes[current_index];
			//se toma cuantos bytes ocupa
			length = (int)ByteBuffer.wrap(bytes, current_index+1, 2).getShort();
			//se obtiene el valor en string
			String tmp = byteSwitch(bytes, offset, length, type);
			//se hace apend de lo que se lee
			result = result.concat("|"+ tmp +"|");
			//se establecen los valores para el proximo subregistro
			current_index = current_index + length + 3;
			offset = offset + length + 3;
		}
		
		return result;
	}
	
	/**
	 * Lee los bytes dados con los limistes dados para crear
	 * un tipo y transformarlo a {@link String} 
	 * 
	 * @param bytes arrglo de bytes que se van a 
	 * transforman a {@link String}
	 * 
	 * @param offset corrimiento en el arreglo de bytes
	 * 
	 * @param length cantidad de bytes que van a ser leidos
	 * 
	 * @param type tipo de la informacion almacenada
	 * 
	 * @return String con lo que se leyo en el subregistro
	 */
	private String byteSwitch(byte[] bytes, int offset, int length, byte type){
		switch(type){
			case(NULL_VALUE):
				return "null";
			case(BY_TYPE_INTEGER):
				int num_int = ByteBuffer.wrap(bytes).getInt(offset);
				return String.valueOf(num_int);
			case(BY_TYPE_DECIMAL):
				float num_fl = ByteBuffer.wrap(bytes).getFloat(offset);
				return String.valueOf(num_fl);
			default:
				return new String(bytes, offset, length);
		}
	}
	
	/**
	 * Retorna todos los datos de la tabla, en forma de LinkedList de LinkedList
	 * 
	 * @param database_name nombre de la base de datos 
	 * 
	 * @param table_name nombre de la tabla
	 * 
	 * @return LinkedList de LinkedList que representa la tabla
	 */
	public LinkedList<LinkedList<String>> getTable(String database_name, String table_name){
		//tabla resultante
		LinkedList<LinkedList<String>> table = new LinkedList<LinkedList<String>>();
		//archivo de la base de datos o esquema
		File file_database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		//si no existe el archivo
		if(!file_database.exists()){
			System.err.format("La base de datos %s no existe\n", database_name);
			return table;
		}
		//si el archivo existe
		else{
			File file_table = new File(file_database, table_name);
			//si no existe la tabla
			if(!file_table.exists()){
				System.err.format("La tabla %s no existe en la base %s\n", table_name, database_name);
			}
			//si la tabla existe
			else{
				//se crean los archivos de tabla
				File file_blocks = new File(file_table, table_name + BLOCKS_SUFFIX);
				File file_tree = new File(file_table, table_name + TREE_SUFIX);
				//si no existe algun archivo
				if(!file_blocks.exists() || !file_tree.exists()){
					System.err.format("La tabla %s en la base de datos %s no existe\n", table_name, database_name);
					return table;
				}
				//si el archivo existe
				else{
					try {
						//arbol de la tabla
						xBplusTreeBytes tree = xBplusTreeBytes.ReOpen(new RandomAccessFile(file_tree, "rw"), 
								new RandomAccessFile(file_blocks, "rw"));
						//llave temporal 
						String tmp_key = tree.NextKey(PK_INDEX);
						
						while(tmp_key != null){
							//registro en bytes
							byte[] tmp_register = tree.get(tmp_key);
							//se crea la fila
							LinkedList<String> tmp_row = byteArray2List(tmp_register);
							//se agrega la fila
							table.add(tmp_row);
							//se cambia a la proxima llave
							tmp_key = tree.NextKey(tmp_key);
						}
						//se retorna la tabla
						return table;
						
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		return table;
	}
	
	/**
	 * Crea una lista de String con los valores de 
	 * de los registros en string
	 * 
	 * @param array arreglo de bytes que corresponde al registro de 
	 * de la fila 
	 * 
	 * @return LinkedList de String que contiene los datos
	 */
	private LinkedList<String> byteArray2List(byte[] array){
		//lista con los string 
		LinkedList<String> result_list = new LinkedList<String>();
		//indice por el cual va 
		int current_index = 0;
		//offset de por donde se va a leer
		int offset = 3;
		//cantidad de bytes que se van a leer
		int length = 0;
		//tipo de valor del que se va a leer
		byte type = (byte)0x00;
		
		while(current_index < array.length){
			//se toma el tipo
			type = array[current_index];
			//se toma cuantos bytes ocupa
			length = (int)ByteBuffer.wrap(array, current_index+1, 2).getShort();
			//se obtiene el valor en string
			String tmp = byteSwitch(array, offset, length, type);
			//se hace apend de lo que se lee
			result_list.add(tmp);
			//se establecen los valores para el proximo subregistro
			current_index = current_index + length + 3;
			offset = offset + length + 3;
		}
		
		return result_list;
		
	}
	
	/**
	 * Elimina una fila por la llave de la fila.
	 * 
	 * @param database_name nombre de la base de datos
	 * 
	 * @param table_name nombre de la tabla donde se va a 
	 * borrar la fila
	 * 
	 * @param key llave que se va a borrar
	 */
	public void deleteRow(String database_name, String table_name, String key){
		//se verifica que exista la carpeta de bases de datos
		File file_database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
		//si el no existe la base de datos
		if(!file_database.exists()){
			System.err.format("La base de datos %s no existe\n", database_name);
		}
		//si existe toma el de la tabla
		else{
			//carpeta de la tabla
			File file_table = new File(file_database, table_name);
			//si no existe
			if(!file_table.exists()){
				System.err.format("La tabla %s de la base de datos %s no existe\n", table_name, database_name);
			}
			//si existe
			else{
				//se verifica que existan los archivos de las tablas 
				File file_blocks = new File(file_table, table_name + BLOCKS_SUFFIX);
				File file_tree = new File(file_table, table_name + TREE_SUFIX);
				//si no existen los archivos de la tabla
				if(!file_blocks.exists() || !file_tree.exists()){
					System.err.format("La tabla con el nombre %s no ha sido creada\n"
							+ "o algun archivo a sido corrompido\n", table_name);
				}
				//si ambos existen
				else{
					try {
						//se crea el arbol
						xBplusTreeBytes tree = xBplusTreeBytes.ReOpen(new RandomAccessFile(file_tree, "rw"), 
								new RandomAccessFile(file_blocks, "rw"));
						//si no esta la llave
						if(!tree.ContainsKey(key)){
							System.err.format("La llave %s no se encuentra en la tabla %s de la base de datos %s\n", key, 
									table_name, database_name);
							tree.Shutdown();
						}
						else{
							tree.RemoveKey(key);
							tree.Commit();
							tree.Shutdown();
							System.out.format("La llave %s fue borrada correctamente de la tabla %s\n", key, table_name);
						}
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
	}
	
	/**
	 * Se actualiza un registro de la tabla
	 * 
	 * @param database_name nombre de la base de datos
	 * 
	 * @param table_name nombre de la tabla donde se va 
	 * a actulizar
	 * 
	 * @param key llave del registro que se va a actualizar
	 * 
	 * @param data nueva informaci�n de que se va a escribir
	 */
	public void updateRegister(String table_name, String key, LinkedList<String> data){
		//se verifica que exista la carpeta de bases de datos
				File file_database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
				//si el no existe la base de datos
				if(!file_database.exists()){
					System.err.format("La base de datos %s no existe\n");
				}
				else{
					File file_table = new File(file_database, table_name);
					
					if(!file_table.exists()){
						System.err.format("La tabla %s de la base %s no existe\n", table_name, database_name);
					}
					else{
						//se verifica que existan los archivos de las tablas 
						File file_blocks = new File(file_table, table_name + BLOCKS_SUFFIX);
						File file_tree = new File(file_table, table_name + TREE_SUFIX);
						//si no existen los archivos de la tabla
						if(!file_blocks.exists() || !file_tree.exists()){
							System.err.format("La tabla con el nombre %s no ha sido creada\n"
									+ "o algun archivo a sido corrompido\n", table_name);
						}
						//si existen
						else{
							try {
								//se crea el arbol
								xBplusTreeBytes tree = xBplusTreeBytes.ReOpen(new RandomAccessFile(file_tree, "rw"), 
										new RandomAccessFile(file_blocks, "rw"));
								//si no esta la llave
								if(!tree.ContainsKey(key)){
									System.err.format("La llave %s no se encuentra en la tabla %s de la base de datos %s\n", key, 
											table_name, database_name);
									tree.Shutdown();
								}
								else{
									//se obtiene la informaci�n de las columnas
									byte[] b_pk_index = tree.get(PK_INDEX);
									int pk_index = (int)ByteBuffer.wrap(b_pk_index).getShort();
									
									String tmp_key = data.get(pk_index);
									if(key.compareTo(tmp_key)!=0){
										System.err.format("Se quiere actualizar un registro con llave %s\n"
												+ "diferente a la llave del registro anterior %s\n", key, tmp_key);
										tree.Shutdown();
									}
									else{
										//metada de la tabla
										byte[] metadata = tree.get(METADATA_KEY);
										//Vector con tipos e informaci�n
										Vector<Pair<String,String>> vec = readMetadata(metadata, data);
										//se convierte el arreglo a bytes
										byte[] register = toBytes(vec);
										//se escribe el nuevo registro
										tree.set(key, register);
										//se actualiza y se cierra el arbol
										tree.Commit();
										tree.Shutdown();
									}
								}
							} catch (FileNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
				
	}
	
	/**
	 * Convierte la metadata y la data en un vector, donde cada 
	 * atributo de la base de datos tiene un indice 
	 * 
	 * @param metadata Informaci�n de la tabla que esta almacenada
	 *  
	 * @param data informaic�n que se va a escribir
	 * 
	 * @return Vector que contiene llaves e informaci�n.
	 */
	private Vector<Pair<String,String>> readMetadata(byte[] metadata, LinkedList<String> data){
		//vector con pares de tipo e informacion
		Vector<Pair<String, String>> result = new Vector<Pair<String, String>>();
		//tama�o del registro
		int length = 0;
		//tipo del registro
		String type = "";
		//posicion actual que se analiza
		int index = 0;
		//posicion de la informaci�n
		int data_index = 0;
		
		while(index < metadata.length){
			//se toma el tama�o
			length = (int)ByteBuffer.wrap(metadata).getShort(index);
			//por cada tipo se hace cambia el tipo
			switch(metadata[index+2]){
			
				case BY_TYPE_INTEGER:
					type = TableAttribute.TYPE_INT;
					break;
					
				case BY_TYPE_DECIMAL:
					type = TableAttribute.TYPE_DECIMAL;
					break;
					
				case BY_TYPE_VARCHAR:
					type = TableAttribute.TYPE_VARCHAR;
					break;
					
				case BY_TYPE_CHAR:
					type = TableAttribute.TYPE_CHAR;
					break;
				
				case BY_TYPE_DATETIME:
					type = TableAttribute.TYPE_DATETIME;
					break;
					
				default:
					System.err.format("Hubo un problema con el tipo de dato\n");
					break;
			}
			
			Pair<String, String> tmp_par = new Pair<String, String>(type, data.get(data_index));
			result.addElement(tmp_par);
			
			index = index + 3 + length;
			data_index++;
		}
		
		return result;
	}
	
	
	/**
	 * Convierte un entero a arreglo de bytes
	 * 
	 * @param integer Entero a convertir
	 * 
	 * @return arreglo de bytes que representa al entero
	 */
	private byte[] int2bytes(int num){
		return ByteBuffer.allocate(4).putInt(num).array();
	}
	
	/**
	 * Convierte un short a un arreglo de bytes
	 * 
	 * @param num short que se va a convertir
	 * 
	 * @return arreglo de bytes que representa el short 
	 */
	private byte[] short2bytes(short num){
		return ByteBuffer.allocate(2).putShort(num).array();
	}
	
	/**
	 * Convierte un float a un arreglo de bytes
	 * 
	 * @param num float que se va a convertir
	 * 
	 * @return arreglo de bytes que representa el float
	 */
	private byte[] float2bytes(float num){
		return ByteBuffer.allocate(4).putFloat(num).array();
	}
	
	/**
	 * Concatena dos arreglos de bytes creando un nuevo arreglo
	 * 
	 * @param by_array1 primer arreglo a concatenar que va al
	 * inicio del nuevo arreglo
	 * 
	 * @param by_array2 segundo arreglo a concatenar que va al
	 * final del nuevo arreglo
	 * 
	 * @return un nuevo arreglo con los dos arreglos concatenados
	 */
	private byte[] byteArrayConcatenate(byte[] by_array1, byte[] by_array2){
		byte[] res = new byte[by_array1.length + by_array2.length];
		
		for (int i = 0; i < by_array1.length; i++) {
			res[i] = by_array1[i];
		}
		
		for (int i = 0; i < by_array2.length; i++) {
			res[by_array1.length+i] = by_array2[i];
		}
		
		return res;
	}
	
	/**
	 * Retorna la lista de Databases que hay creadas
	 * 
	 * @return LinkedList de String con los nombres 
	 * de las bases de datas creadas
	 */
	public LinkedList<String> getDatabases(){
		
		File databases = new File(DATABASES_PATH);
		
		String[] str_databases = databases.list();
		
		LinkedList<String> list = new LinkedList<String>();
		
		for (int i = 0; i < str_databases.length; i++) {
			list.add(str_databases[i]);
		}
		
		return list;
	}
	
	/**
     * Se elimina la tabla de la base de datos
     * 
     * @param table_name nombre de la tabla
     */
    public void dropTable(String table_name){
    	File database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
    	if(!database.exists()){
    		System.err.format("La base de datos %s no existe\n", database_name);
    	}
    	else{
    		File table = new File(database, table_name);
    		if(!table.exists()){
    			System.err.format("La tabla %s no existe en la base de datos\n" , table_name);
    		}
    		else{
    			recursiveFileDelete(table);
    		}
    	}
    }
    
    /**
     * Verifica si una llave esta en una tabla
     * 
     * @param table_name nombre de la tabla
     * 
     * @param key llave que se busca en el arbol
     * 
     * @return boolean que es true si se encuentra y 
     * false si no.
     */
    public boolean isColumn(String table_name, String key){
    	boolean result = false;
    	File database = new File(DATABASES_PATH + FILE_SEPARATOR + database_name);
    	if(!database.exists()){
    		System.err.format("La base de datos %s no existe\n", database_name);
    	}else{
    		File table = new File(database, table_name);
    		if(!table.exists()){
    			System.err.format("La tabla %s no existe en la base de datos\n" , table_name);
    		}
    		else{
    			//se verifica que existan los archivos de las tablas 
				File file_blocks = new File(table, table_name + BLOCKS_SUFFIX);
				File file_tree = new File(table, table_name + TREE_SUFIX);
				//si no existen los archivos de la tabla
				if(!file_blocks.exists() || !file_tree.exists()){
					System.err.format("La tabla con el nombre %s no ha sido creada\n"
							+ "o algun archivo a sido corrompido\n", table_name);
				}
				
				else{
					try {
						xBplusTreeBytes tree = xBplusTreeBytes.ReOpen(new RandomAccessFile(file_tree, "rw"), 
								new RandomAccessFile(file_blocks, "rw"));
						
						result = tree.ContainsKey(key);
						
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
    		}
    	}
    	
    	
    	return result;
    }

    
    
    
}
