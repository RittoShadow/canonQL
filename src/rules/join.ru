PREFIX ex:  <http://example.org/>

DELETE { ?b0 ex:arg ?b1 .
		?b1 ex:arg ?q .
		?b1 ex:type ex:join }
INSERT { ?b0 ex:arg ?q }
WHERE
  { 
  	{
  		SELECT DISTINCT ?b0 ?b1 ?q
  		WHERE{
  			?b0 ex:type ex:join .
  			?b0 ex:arg ?b1 .
		  	?b1 ex:type ex:join .
		  	?b1 ex:arg ?q .
		  	?q ex:type ex:TP .
		  	FILTER NOT EXISTS{
		  		?b1 ex:arg ?a .
		  		?a ex:type ?type .
		  		FILTER(?type != ex:TP)
		  	}
		  }
  	} 	
  } 