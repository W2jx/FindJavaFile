package com.wjx.find.findtransaction;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.poi.excel.BigExcelWriter;
import cn.hutool.poi.excel.ExcelUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FindJavaWithTransactionRollbackfor {

    public static void main(String[] args) {
        long l = System.currentTimeMillis();
        System.out.println("开始");

        //项目路径
//        String fielPath = "D:\\mysoftware\\git\\gitreporsitory\\liferay-portal\\";
        String fielPath = "D:\\mysoftware\\git\\gitreporsitory\\liferay-portal";

        //获取指定路径下的全部文件
//        fielPath = fielPath + "modules";
        List<File> files = FileUtil.loopFiles(fielPath);

        System.out.println("allSize:"+files.size());

        //初始化表格中数据内容
        ArrayList<ArrayList<String>> arrayLists = new ArrayList<>();

        int currentFileNum = 1;
        int javaFileNum = 1;

        //遍历处理文件
        for (File file : files) {

            //确定是java文件后才处理
            if("java".equals(FileNameUtil.extName(file))){

                //收集文件名称及路径信息
                String name = file.getName();
                String path = file.getPath();
                path = path.substring(fielPath.length(), path.length() - name.length() - 1);

                try {
                    //判断文件是否符合要求
                    if(isSetReference(file)){
                        arrayLists.add(CollUtil.newArrayList(path, name));
                        System.out.println(file.getAbsolutePath());
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                System.out.println("当前java文件书:"+ javaFileNum++);
            }

            System.out.println("文件总数:"+files.size()+",当前文件数:"+ currentFileNum++);
        }

        //生成表格
        creatXlsx("E:\\test\\test.xlsx", arrayLists);

        System.out.println("结束，总用时："+ (System.currentTimeMillis() - l));
    }



    private static boolean isSetReference(File file) throws FileNotFoundException {

        //解析java文件
        Optional<CompilationUnit> result = new JavaParser().parse(file).getResult();

        if(result.isPresent()){
            NodeList<TypeDeclaration<?>> types = result.get().getTypes();
            for (TypeDeclaration<?> type : types) {
                //判断是否有指定注解Transactional存在
                Optional<AnnotationExpr> annotationTransactional = type.getAnnotationByName("Transactional");
                if(annotationTransactional.isPresent()){
                    AnnotationExpr Transactional = annotationTransactional.get();

                    //获取参数
                    if (Transactional.toNormalAnnotationExpr().isPresent()) {
                        NodeList<MemberValuePair> pairs = Transactional.toNormalAnnotationExpr().get().getPairs();
                        //判断注解中是否有rollbackFor或者rollbackForClassName参数存在
                        boolean hasRollbackFor = false;
                        for (MemberValuePair pair : pairs) {
                            if("rollbackFor".equals(pair.getNameAsString()) || "rollbackForClassName".equals(pair.getNameAsString())){
                                hasRollbackFor = true;
                                break;
                            }
                        }
                        //判断文件是否有指定的注解并且是接口
                        if(hasRollbackFor && type.asClassOrInterfaceDeclaration().isInterface()){

                            //如果类中存在一个方法上的Transactional注解，没有readonly=true，也没有enabled=false，还没有rollbackFor或者rollbackForClassName参数，则这个类是需要的类
                            NodeList<BodyDeclaration<?>> members = type.getMembers();
                            methodMember: for (BodyDeclaration<?> member : members) {

                                //判断节点是否是方法
                                if(member instanceof MethodDeclaration){

                                    //按名称查找方法上的注解
                                    Optional<AnnotationExpr> methodTransactionalAnnotation = member.getAnnotationByName("Transactional");

                                    //注解是否存在
                                    if(methodTransactionalAnnotation.isPresent()){
                                        AnnotationExpr methodTransactional = methodTransactionalAnnotation.get();
                                        if (methodTransactional.toNormalAnnotationExpr().isPresent()) {
                                            NodeList<MemberValuePair> pairs1 = methodTransactional.toNormalAnnotationExpr().get().getPairs();

                                            for (MemberValuePair memberValuePair : pairs1) {
                                                if("readOnly".equals(memberValuePair.getNameAsString())){
                                                    if("true".equals(memberValuePair.getValue().toString().toLowerCase())){
                                                        continue methodMember;
                                                    }
                                                }
                                                if("enabled".equals(memberValuePair.getNameAsString())){
                                                    if("false".equals(memberValuePair.getValue().toString().toLowerCase())){
                                                        continue methodMember;
                                                    }
                                                }
                                                if("rollbackFor".equals(memberValuePair.getNameAsString()) || "rollbackForClassName".equals(memberValuePair.getNameAsString())){
                                                    continue methodMember;
                                                }
                                            }
                                            System.out.println("该方法符合要求："+((MethodDeclaration) member).getName());
                                            return true;
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }


    public static void creatXlsx(String xlsxPath, ArrayList<ArrayList<String>> arrayLists){
        File file = FileUtil.file(xlsxPath);
        if(file.exists()){
            FileUtil.del(file);
        }
        BigExcelWriter writer= ExcelUtil.getBigWriter(xlsxPath);
        writer.write(arrayLists, true);
        writer.close();
    }
}
