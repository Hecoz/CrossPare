path <- getwd()
folds <- dir("../",full.names=T,all.files=F,recursive=T)
folds <- list.dirs("../")

for(fold in folds){
  files <- list.files(fold)
  for(file in files){
    file <-  file.path(fold,file, fsep=.Platform$file.sep)
    if (file.exists(file) & grepl(".csv",file)) { 
        changeToZero(file)
    }
  }
}

changeToZero <- function(file) {

  if (!file.exists(file)) { return(NULL) }
  all.data <- read.csv(file,header = TRUE)
  all.data$Defective <- ifelse(all.data$Defective == "1", 1, 0)
  
  write.csv(all.data,file = file,sep = "",row.names = FALSE)
}

