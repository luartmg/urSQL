package urSQL.StoredDataManager.BplusJ;

	public class LinkedFileException extends Exception //ApplicationException 
	{
		public LinkedFileException(String message)//: base(message) 
		{
			super(message);
			// do nothing extra
		}
	}