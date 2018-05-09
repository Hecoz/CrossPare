library("effsize")

strategys <- c("Nam15","Peters15","Turhan09")
projects  <- c("AEEEM-Mask","MORPH-Mask","RELINK-Mask","SOFTLAB-Mask")
methods   <- c("NB","RF","DT","LR","NET","SVM")

for (sidx in seq(length(strategys))) {
  tmeans <- NULL
  tstd   <- NULL
  strategy <- strategys[sidx]
  for(pidx in seq(length(projects))){
    project <- projects[pidx]
    filename <- file.path("..","01results-csv",paste(project,"-",strategy,".csv",sep=""), fsep=.Platform$file.sep)
    alldata <- read.csv(filename,header=T)
    
    data.tmp <- data.frame(alldata[,"version"])
    colnames(data.tmp) <- c("version")
    for(method in methods){
      fcol  <- paste("fscore_",method,sep = "")
      aucol <- paste("auc_",method,sep = "")
      data.tmp  <- cbind(data.tmp,alldata[,c(fcol,aucol)])
    }
    nrows <- nrow(data.tmp)
    pnum <- nrows/10
    adds <- pnum - 1
    data <- NULL
    for(i in seq(pnum)){
      for(j in seq(i,nrows,by = pnum)){
        data <- rbind(data,data.tmp[j,])
      }
    }

    for(i in seq(1,nrows,by = 10)){
      #tmeans <- rbind(tmeans,colMeans(data[i:(i+9),2:13]))
      tmeans <- rbind(tmeans,apply(data[i:(i+9),2:13], 2, mean))
    }
    for(i in seq(1,nrows,by = 10)){
      tstd <- rbind(tstd,apply(data[i:(i+9),2:13], 2, sd))
    }
  }
  results <- cbind(tmeans,tstd)
  filename <- file.path("..","03output",paste(strategy,".csv",sep=""), fsep=.Platform$file.sep)
  write.csv(results,filename)
}