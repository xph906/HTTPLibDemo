private void processJSONString(String str){
	Gson gson = new Gson();
	NetProphetHTTPRequestInfoObject[] reqRS = null;
	NetProphetNetworkData[] netRS = null;
	try{
		reqRS = gson.fromJson(str, NetProphetHTTPRequestInfoObject[].class);
		if(reqRS==null || reqRS.length==0 || reqRS[0].getUrl()==null){
			reqRS = null;
			netRS = gson.fromJson(str, NetProphetNetworkData[].class);
		}
	}
	catch(Exception e){
		System.err.println(e);
	}
	
	//Now we have the objects.
	if(reqRS != null){
		System.out.println("length of reqRS: "+reqRS.length);
		for(NetProphetHTTPRequestInfoObject obj : reqRS){
			//do something here
			System.out.println("  "+obj.getUrl()+" delay:"+obj.getOverallDelay());
		}
	}
	else{
		//System.out.println("length of reqRS: 0");
	}
	
	if(netRS != null){
		System.out.println("length of netRS: "+netRS.length);
		for(NetProphetNetworkData obj : netRS){
			//do something here
			System.out.println("  networkType:"+obj.getNetworkType());
		}
	}
	else{
		//System.out.println("length of netRS: 0");
	}
}
	
public void testProcessingJSONString(String filename){
	try {
		FileInputStream fstream = new FileInputStream(filename);
		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

		String strLine;
		LinkedList<String> jsonStrings = new LinkedList<String>();
		while ((strLine = br.readLine()) != null)   {
			jsonStrings.add(strLine);
		  	// start to process JSON string.
		  	processJSONString(strLine);
		}
		System.out.println("handled "+jsonStrings.size()+" lines of strings");
		br.close();
	} catch (IOException e) {
		e.printStackTrace();
	} 		
}

testProcessingJSONString("jsonstrings.txt");